package net.minecraft.server;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkProviderServer implements IChunkProvider {

    private static final Logger b = LogManager.getLogger();
    private Set<Long> unloadQueue = Collections.newSetFromMap(new ConcurrentHashMap());
    public Chunk emptyChunk;
    public IChunkProvider chunkProvider;
    private IChunkLoader chunkLoader;
    public boolean forceChunkLoad = true;
    private LongHashMap<Chunk> chunks = new LongHashMap();
    private List<Chunk> chunkList = Lists.newArrayList();
    public WorldServer world;

    public ChunkProviderServer(WorldServer worldserver, IChunkLoader ichunkloader, IChunkProvider ichunkprovider) {
        this.emptyChunk = new EmptyChunk(worldserver, 0, 0);
        this.world = worldserver;
        this.chunkLoader = ichunkloader;
        this.chunkProvider = ichunkprovider;
    }

    public boolean isChunkLoaded(int i, int j) {
        return this.chunks.contains(ChunkCoordIntPair.a(i, j));
    }

    public List<Chunk> a() {
        return this.chunkList;
    }

    public void queueUnload(int i, int j) {
        if (this.world.worldProvider.e()) {
            if (!this.world.c(i, j)) {
                this.unloadQueue.add(Long.valueOf(ChunkCoordIntPair.a(i, j)));
            }
        } else {
            this.unloadQueue.add(Long.valueOf(ChunkCoordIntPair.a(i, j)));
        }

    }

    public void b() {
        Iterator iterator = this.chunkList.iterator();

        while (iterator.hasNext()) {
            Chunk chunk = (Chunk) iterator.next();

            this.queueUnload(chunk.locX, chunk.locZ);
        }

    }

    public Chunk getChunkAt(int i, int j) {
        long k = ChunkCoordIntPair.a(i, j);

        this.unloadQueue.remove(Long.valueOf(k));
        Chunk chunk = (Chunk) this.chunks.getEntry(k);

        if (chunk == null) {
            chunk = this.loadChunk(i, j);
            if (chunk == null) {
                if (this.chunkProvider == null) {
                    chunk = this.emptyChunk;
                } else {
                    try {
                        chunk = this.chunkProvider.getOrCreateChunk(i, j);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.a(throwable, "Exception generating new chunk");
                        CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Chunk to be generated");

                        crashreportsystemdetails.a("Location", (Object) String.format("%d,%d", new Object[] { Integer.valueOf(i), Integer.valueOf(j)}));
                        crashreportsystemdetails.a("Position hash", (Object) Long.valueOf(k));
                        crashreportsystemdetails.a("Generator", (Object) this.chunkProvider.getName());
                        throw new ReportedException(crashreport);
                    }
                }
            }

            this.chunks.put(k, chunk);
            this.chunkList.add(chunk);
            chunk.addEntities();
            chunk.loadNearby(this, this, i, j);
        }

        return chunk;
    }

    public Chunk getOrCreateChunk(int i, int j) {
        Chunk chunk = (Chunk) this.chunks.getEntry(ChunkCoordIntPair.a(i, j));

        return chunk == null ? (!this.world.ad() && !this.forceChunkLoad ? this.emptyChunk : this.getChunkAt(i, j)) : chunk;
    }

    public Chunk loadChunk(int i, int j) {
        if (this.chunkLoader == null) {
            return null;
        } else {
            try {
                Chunk chunk = this.chunkLoader.a(this.world, i, j);

                if (chunk != null) {
                    chunk.setLastSaved(this.world.getTime());
                    if (this.chunkProvider != null) {
                        this.chunkProvider.recreateStructures(chunk, i, j);
                    }
                }

                return chunk;
            } catch (Exception exception) {
                ChunkProviderServer.b.error("Couldn\'t load chunk", exception);
                return null;
            }
        }
    }

    public void saveChunkNOP(Chunk chunk) {
        if (this.chunkLoader != null) {
            try {
                this.chunkLoader.b(this.world, chunk);
            } catch (Exception exception) {
                ChunkProviderServer.b.error("Couldn\'t save entities", exception);
            }

        }
    }

    public void saveChunk(Chunk chunk) {
        if (this.chunkLoader != null) {
            try {
                chunk.setLastSaved(this.world.getTime());
                this.chunkLoader.a(this.world, chunk);
            } catch (IOException ioexception) {
                ChunkProviderServer.b.error("Couldn\'t save chunk", ioexception);
            } catch (ExceptionWorldConflict exceptionworldconflict) {
                ChunkProviderServer.b.error("Couldn\'t save chunk; already in use by another instance of Minecraft?", exceptionworldconflict);
            }

        }
    }

    public void getChunkAt(IChunkProvider ichunkprovider, int i, int j) {
        Chunk chunk = this.getOrCreateChunk(i, j);

        if (!chunk.isDone()) {
            chunk.n();
            if (this.chunkProvider != null) {
                this.chunkProvider.getChunkAt(ichunkprovider, i, j);
                chunk.e();
            }
        }

    }

    public boolean a(IChunkProvider ichunkprovider, Chunk chunk, int i, int j) {
        if (this.chunkProvider != null && this.chunkProvider.a(ichunkprovider, chunk, i, j)) {
            Chunk chunk1 = this.getOrCreateChunk(i, j);

            chunk1.e();
            return true;
        } else {
            return false;
        }
    }

    public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate) {
        int i = 0;
        ArrayList arraylist = Lists.newArrayList(this.chunkList);

        for (int j = 0; j < arraylist.size(); ++j) {
            Chunk chunk = (Chunk) arraylist.get(j);

            if (flag) {
                this.saveChunkNOP(chunk);
            }

            if (chunk.a(flag)) {
                this.saveChunk(chunk);
                chunk.f(false);
                ++i;
                if (i == 24 && !flag) {
                    return false;
                }
            }
        }

        return true;
    }

    public void c() {
        if (this.chunkLoader != null) {
            this.chunkLoader.b();
        }

    }

    public boolean unloadChunks() {
        if (!this.world.savingDisabled) {
            for (int i = 0; i < 100; ++i) {
                if (!this.unloadQueue.isEmpty()) {
                    Long olong = (Long) this.unloadQueue.iterator().next();
                    Chunk chunk = (Chunk) this.chunks.getEntry(olong.longValue());

                    if (chunk != null) {
                        chunk.removeEntities();
                        this.saveChunk(chunk);
                        this.saveChunkNOP(chunk);
                        this.chunks.remove(olong.longValue());
                        this.chunkList.remove(chunk);
                    }

                    this.unloadQueue.remove(olong);
                }
            }

            if (this.chunkLoader != null) {
                this.chunkLoader.a();
            }
        }

        return this.chunkProvider.unloadChunks();
    }

    public boolean canSave() {
        return !this.world.savingDisabled;
    }

    public String getName() {
        return "ServerChunkCache: " + this.chunks.count() + " Drop: " + this.unloadQueue.size();
    }

    public List<BiomeBase.BiomeMeta> getMobsFor(EnumCreatureType enumcreaturetype, BlockPosition blockposition) {
        return this.chunkProvider.getMobsFor(enumcreaturetype, blockposition);
    }

    public BlockPosition findNearestMapFeature(World world, String s, BlockPosition blockposition) {
        return this.chunkProvider.findNearestMapFeature(world, s, blockposition);
    }

    public int getLoadedChunks() {
        return this.chunks.count();
    }

    public void recreateStructures(Chunk chunk, int i, int j) {}

    public Chunk getChunkAt(BlockPosition blockposition) {
        return this.getOrCreateChunk(blockposition.getX() >> 4, blockposition.getZ() >> 4);
    }
}
