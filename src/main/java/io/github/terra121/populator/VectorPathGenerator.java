package io.github.terra121.populator;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.ICubicPopulator;
import io.github.terra121.TerraMod;
import io.github.terra121.dataset.Heights;
import io.github.terra121.dataset.OpenStreetMaps;
import io.github.terra121.dataset.Pathway;
import io.github.terra121.projection.GeographicProjection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import io.github.terra121.dataset.Pathway.VectorPath;
import java.util.*;

public class VectorPathGenerator implements ICubicPopulator {

    OpenStreetMaps osm;
    Heights heights;
    GeographicProjection projection;

    public VectorPathGenerator(OpenStreetMaps.Type type, Heights heights, GeographicProjection proj) {
        this.heights = heights;
        projection = proj;
        this.osm = Pathway.getOSM(proj, type);
    }

    @Override
    public void generate(World world, Random random, CubePos cubePos, Biome biome) {

        int cubeX = cubePos.getX();
        int cubeY = cubePos.getY();
        int cubeZ = cubePos.getZ();
        Set<OpenStreetMaps.Edge> edges = osm.chunkStructures(cubeX, cubeY);
        Set<Pathway.ChunkWithStructures> farEdges = ExtendedRenderer.osmStructures;
        // avoid ConcurrentModificationException??? (hopefully...)
        Set<Pathway.ChunkWithStructures> test = farEdges;
        if (edges == null) edges = new HashSet<>();

        // add far edges to be processed
        if (test != null) {
            for (Pathway.ChunkWithStructures e : test) {
                if (e.chunk != null && e.structures != null) {
                    if (e.chunk != cubePos) {
                        edges.addAll(e.structures);
                    }
                }
            }
        }
        // todo delete
        TerraMod.LOGGER.info("size: {}", edges.size());

        List<Pathway.VectorPathGroup> paths = Pathway.chunkStructuresAsVectors(edges, world, cubeX, cubeY, cubeZ, heights, projection, null, null);
        List<Pathway.VectorPathGroup> sPaths = new ArrayList<>();
        List<Pathway.VectorPathGroup> secondProcessPaths;
        List<OpenStreetMaps.Edge> edgeCache = new ArrayList<>();
        Pathway.VectorPoint startCache = null;
        Pathway.VectorPoint endCache;

        if (!paths.isEmpty()) {

            // iterate over VectorPathGroups
            for (Pathway.VectorPathGroup vpg : paths) {

                List<VectorPath> currentVp = vpg.paths;

                for (int e = 0; e <= currentVp.size() - 1; e++) {

                    VectorPath current = currentVp.get(e);
                    VectorPath next;

                    try {
                        next = currentVp.get(e + 1);
                    } catch (IndexOutOfBoundsException ignore) {
                        next = current;
                    }

                    if (current.edge != null) {

                        edgeCache.add(current.edge);

                    } else if (!current.path.isEmpty()) {

                        if (next.edge != null) {

                            startCache = new Pathway.VectorPoint(current.path.get(current.path.size() - 1), current.relations);

                        }

                        if (!edgeCache.isEmpty()) {

                            endCache = new Pathway.VectorPoint(current.path.get(0), current.relations);
                            Set<OpenStreetMaps.Edge> tunnels = new HashSet<>(edgeCache);
                            secondProcessPaths = Pathway.chunkStructuresAsVectors(tunnels, world, cubeX, cubeY, cubeZ, heights, projection, startCache, endCache);
                            sPaths.addAll(secondProcessPaths);

                        }
                    }
                }
            }

            sPaths.addAll(paths);

            for (Pathway.VectorPathGroup g : sPaths) {
                placeVectorPaths(g.paths, world);
            }

        }
    }

    public void placeVectorPaths(List<VectorPath> paths, World world) {
        for (VectorPath p : paths) {
            for (Vec3d path : p.path) {
                BlockPos l = new BlockPos(path.x, path.y, path.z);
                if (world.getBlockState(l).getBlock().getDefaultState() != p.material) {
                    world.setBlockState(l, p.material);
                }
            }
        }
    }

    public static double bound(double x, double slope, double j, double k, double r, double x0, double b, double sign) {
        double slopeSign = sign * (slope < 0 ? -1 : 1);

        if (x < j - slopeSign * x0) { //left circle
            return slope * j + sign * Math.sqrt(r * r - (x - j) * (x - j));
        }
        if (x > k - slopeSign * x0) { //right circle
            return slope * k + sign * Math.sqrt(r * r - (x - k) * (x - k));
        }
        return slope * x + sign * b;
    }

}
