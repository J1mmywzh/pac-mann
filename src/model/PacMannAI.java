package model;

import java.util.*;
import java.util.stream.StreamSupport;
import graph.Pathfinding;
import model.MazeGraph.IPair;
import model.MazeGraph.MazeEdge;
import model.MazeGraph.MazeVertex;

/**
 * "Advanced" PacMann AI that uses a combination of:
 * - Predictive ghost avoidance
 * - Strategic pellet consumption
 * - Dot collection optimization
 * - Dynamic path planning
 */
public class PacMannAI extends PacMann {
    // Core parameters
    private static final int SEARCH_DEPTH = 10;
    private static final double DOT_SCORE = 10.0;
    private static final double PELLET_SCORE = 25.0;
    private static final double GHOST_EAT_SCORE = 200.0;
    private static final double GHOST_THREAT_PENALTY = -500.0;

    // Distance thresholds
    private static final int DANGER_RADIUS = 4;      // Immediate ghost threat radius
    private static final int PELLET_CHASE_RADIUS = 6; // Distance to consider hunting for pellets

    // Ghost-specific weights
    private static final double BLINKY_THREAT_WEIGHT = 1.5;  // Blinky most dangerous
    private static final double PINKY_THREAT_WEIGHT = 1.3;   // Pinky tries to ambush
    private static final double INKY_THREAT_WEIGHT = 1.2;    // Inky coordinates with Blinky
    private static final double CLYDE_THREAT_WEIGHT = 0.8;   // Clyde least dangerous

    // Threshold for pellet adjustment strategy
    private static final int DOT_THRESHOLD_LOW = 15;
    private static final int DOT_THRESHOLD_MED = 40;

    // Random component for breaking decision ties / infinite loops
    private final Random random;

    /**
     * Constructs a PacMannAI with a reference to the game model
     *
     * @param model The game model containing state information
     */
    public PacMannAI(GameModel model) {
        super(model);
        this.random = new Random();
    }

    /**
     * Decides the next edge for PacMann to traverse based on the current game state
     * This is the main decision-making method called by the game engine
     */
    @Override
    public MazeEdge nextEdge() {
        MazeVertex currentPosition = nearestVertex();
        MazeEdge previousEdge = location().edge();
        List<Ghost> ghosts = getGhosts();

        // Special case: If at a junction with 3+ exits, sometimes use randomness to be unpredictable
        List<MazeEdge> exits = getValidExits(currentPosition, previousEdge);
        if (exits.size() >= 3 && random.nextDouble() < 0.15) {
            return exits.get(random.nextInt(exits.size()));
        }

        // Analyze game state to determine optimal strategy
        boolean inDanger = isInDanger(currentPosition, ghosts);
        boolean shouldEatPellet = shouldPrioritizePellets(currentPosition, ghosts);

        // Create a priority queue for path options
        PriorityQueue<PathOption> options = new PriorityQueue<>(
                Comparator.comparingDouble(option -> -option.score)
        );

        // Evaluate immediate exit options
        for (MazeEdge exit : exits) {
            double score = evaluatePath(exit, inDanger, shouldEatPellet, ghosts);
            options.add(new PathOption(exit, score, 1));
        }

        // If no immediate good options or complex situation, perform deeper search
        if (options.isEmpty() || inDanger || shouldEatPellet) {
            performDeepSearch(currentPosition, previousEdge, options, ghosts, inDanger, shouldEatPellet);
        }

        // Select best available edge or continue current path if no better options
        return options.isEmpty() ?
                (exits.isEmpty() ? previousEdge : exits.get(0)) :
                options.peek().edge;
    }

    /**
     * Gets a list of all valid edges PacMann can take from the current vertex
     * excluding backtracking to the previous edge
     */
    private List<MazeEdge> getValidExits(MazeVertex vertex, MazeEdge prevEdge) {
        List<MazeEdge> validExits = new ArrayList<>();

        for (MazeEdge edge : vertex.outgoingEdges()) {
            // Don't backtrack unless it's the only option
            if (prevEdge != null && edge.dst().equals(prevEdge.src())) {
                continue;
            }
            validExits.add(edge);
        }

        return validExits;
    }

    /**
     * Performs a deeper search to evaluate path options beyond immediate exits
     * Uses a breadth-first approach to explore potential paths up to SEARCH_DEPTH
     */
    private void performDeepSearch(MazeVertex start, MazeEdge prevEdge,
            PriorityQueue<PathOption> options,
            List<Ghost> ghosts, boolean inDanger, boolean pelletMode) {
        // Track visited vertices to avoid cycles
        Set<MazeVertex> visited = new HashSet<>();
        visited.add(start);

        // Create queue for breadth-first search
        Queue<SearchNode> searchQueue = new LinkedList<>();

        // Seed search with initial edges
        for (MazeEdge edge : getValidExits(start, prevEdge)) {
            searchQueue.add(new SearchNode(edge, edge.dst(), 1, visited));
        }

        // Process search queue
        while (!searchQueue.isEmpty() && options.size() < 10) {
            SearchNode node = searchQueue.poll();

            // Calculate score for this path
            double pathScore = evaluateVertex(node.vertex, ghosts, inDanger, pelletMode);

            // Add this option to our consideration set
            options.add(new PathOption(node.firstEdge, pathScore, node.depth));

            // Stop expanding if we've reached max depth
            if (node.depth >= SEARCH_DEPTH) {
                continue;
            }

            // Expand search to neighboring vertices
            for (MazeEdge edge : node.vertex.outgoingEdges()) {
                // Skip if we'd backtrack or visit already seen vertex
                if (node.visited.contains(edge.dst())) {
                    continue;
                }

                // Create new visited set with current vertex added
                Set<MazeVertex> newVisited = new HashSet<>(node.visited);
                newVisited.add(edge.dst());

                // Add this path to search queue
                searchQueue.add(new SearchNode(
                        node.firstEdge, edge.dst(), node.depth + 1, newVisited
                ));
            }
        }
    }

    /**
     * Evaluates a potential path by considering the immediate edge and destination
     */
    private double evaluatePath(MazeEdge edge, boolean inDanger, boolean pelletMode, List<Ghost> ghosts) {
        MazeVertex destination = edge.dst();
        double score = evaluateVertex(destination, ghosts, inDanger, pelletMode);

        // Adjust score based on edge weight (prefer faster paths)
        score -= (edge.weight() - 1.0) * 5.0;

        return score;
    }

    /**
     * Determines whether PacMann is currently in immediate danger from ghosts
     */
    private boolean isInDanger(MazeVertex current, List<Ghost> ghosts) {
        for (Ghost ghost : ghosts) {
            // Only consider ghosts in CHASE state
            if (ghost.state() != Ghost.GhostState.CHASE) {
                continue;
            }

            // Calculate distance to ghost
            int distance = getDistance(current, ghost.nearestVertex());

            // Apply ghost-specific danger threshold adjustments
            double threatMultiplier = getGhostThreatMultiplier(ghost);
            int adjustedRadius = (int)(DANGER_RADIUS * threatMultiplier);

            if (distance <= adjustedRadius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a specific threat multiplier for each ghost type based on their behavior
     */
    private double getGhostThreatMultiplier(Ghost ghost) {
        if (ghost == model.blinky()) return BLINKY_THREAT_WEIGHT;
        if (ghost == model.pinky()) return PINKY_THREAT_WEIGHT;
        if (ghost == model.inky()) return INKY_THREAT_WEIGHT;
        if (ghost == model.clyde()) return CLYDE_THREAT_WEIGHT;
        return 1.0;
    }

    /**
     * Determines whether pellet consumption should be prioritized based on
     * current game state, ghost positions, and remaining dots
     */
    private boolean shouldPrioritizePellets(MazeVertex current, List<Ghost> ghosts) {
        // Count remaining dots
        int remainingDots = countRemainingDots();

        // Count nearby ghosts in CHASE state
        int nearbyGhosts = 0;
        for (Ghost ghost : ghosts) {
            if (ghost.state() != Ghost.GhostState.CHASE) {
                continue;
            }
            int distance = getDistance(current, ghost.nearestVertex());
            if (distance <= PELLET_CHASE_RADIUS) {
                nearbyGhosts++;
            }
        }

        // Different strategies based on game progress
        if (remainingDots < DOT_THRESHOLD_LOW) {
            // Late game: Always prioritize pellets when in danger
            return nearbyGhosts >= 1;
        } else if (remainingDots < DOT_THRESHOLD_MED) {
            // Mid game: Be more strategic with pellet usage
            return nearbyGhosts >= 2;
        } else {
            // Early game: Only use pellets when multiple ghosts nearby
            return nearbyGhosts >= 3;
        }
    }

    /**
     * Evaluates a vertex based on items present, ghost proximity, and game state
     */
    private double evaluateVertex(MazeVertex vertex, List<Ghost> ghosts,
            boolean inDanger, boolean pelletMode) {
        double score = 0;

        // Score based on items
        GameModel.Item item = model.itemAt(vertex);
        if (item == GameModel.Item.DOT) {
            score += DOT_SCORE;
        } else if (item == GameModel.Item.PELLET) {
            // Increase pellet value when in danger or pellet consumption prioritized
            double pelletValue = PELLET_SCORE;
            if (inDanger || pelletMode) {
                pelletValue *= 3;
            }
            score += pelletValue;
        }

        // Add ghost-related scores
        for (Ghost ghost : ghosts) {
            // Calculate distance to ghost
            int distance = getDistance(vertex, ghost.nearestVertex());

            if (ghost.state() == Ghost.GhostState.FLEE) {
                // Points for eating ghosts in FLEE state
                if (distance == 0) {
                    score += GHOST_EAT_SCORE;
                } else if (distance <= 3) {
                    // Chase fleeing ghosts that are close
                    score += GHOST_EAT_SCORE / (distance + 1);
                }
            } else if (ghost.state() == Ghost.GhostState.CHASE) {
                // Apply ghost-specific threat weights
                double threatWeight = getGhostThreatMultiplier(ghost);

                // Heavy penalty for collision
                if (distance == 0) {
                    return GHOST_THREAT_PENALTY * threatWeight; // Immediate high danger
                }

                // Create potential field around ghosts, stronger for more dangerous ghosts
                double repulsion = 100 * threatWeight / (distance * distance + 1);
                score -= repulsion;

                // Additional penalty when in danger zone
                if (distance <= DANGER_RADIUS) {
                    score -= (DANGER_RADIUS - distance + 1) * 50 * threatWeight;
                }

                // When in danger, heavily favor increasing distance from ghosts
                if (inDanger && distance < DANGER_RADIUS) {
                    // Predict ghost's next target based on its behavior
                    MazeVertex ghostTarget = predictGhostTarget(ghost, nearestVertex());

                    // If ghost's target is near this vertex, avoid it more strongly
                    if (getDistance(vertex, ghostTarget) <= 2) {
                        score -= 200 * threatWeight;
                    }
                }
            }
        }

        // Add small random component to break ties unpredictably
        score += random.nextDouble() * 0.5;

        return score;
    }

    /**
     * Predicts where a ghost is targeting based on its behavior
     * Uses knowledge of each ghost's targeting algorithm
     */
    private MazeVertex predictGhostTarget(Ghost ghost, MazeVertex pacPosition) {
        int width = model.width();
        int height = model.height();

        // Determine which ghost we're dealing with and predict accordingly
        if (ghost == model.blinky()) {
            // Blinky directly targets PacMann
            return pacPosition;
        } else if (ghost == model.pinky()) {
            // Pinky targets 4 tiles ahead of PacMann
            MazeEdge edge = location().edge();
            if (edge != null) {
                int di = 0, dj = 0;

                // Determine offset based on PacMann's direction
                switch (edge.direction()) {
                    case LEFT:  di = -4; break;
                    case RIGHT: di = 4;  break;
                    case UP:    dj = -4; break;
                    case DOWN:  dj = 4;  break;
                }

                // Get PacMann's position and apply offset
                IPair pacPos = pacPosition.loc();
                return model.graph().closestTo(pacPos.i() + di, pacPos.j() + dj);
            }
            return model.graph().closestTo(width - 3, 2); // Default corner
        } else if (ghost == model.inky()) {
            // Inky uses vector from Blinky through PacMann
            MazeVertex blinkyPos = model.blinky().nearestVertex();
            IPair pacPos = pacPosition.loc();

            // Calculate the vector from Blinky through PacMann
            int i = 2 * pacPos.i() - blinkyPos.loc().i();
            int j = 2 * pacPos.j() - blinkyPos.loc().j();

            return model.graph().closestTo(i, j);
        } else if (ghost == model.clyde()) {
            // Clyde targets PacMann when far, but runs away when close
            IPair pacPos = pacPosition.loc();
            IPair clydePos = ghost.nearestVertex().loc();

            // Calculate Euclidean distance
            double distance = Math.sqrt(
                    Math.pow(pacPos.i() - clydePos.i(), 2) +
                            Math.pow(pacPos.j() - clydePos.j(), 2)
            );

            if (distance >= 8) {
                return pacPosition; // Target PacMann when far away
            } else {
                // Target random location when close
                return model.graph().closestTo(
                        random.nextInt(width), random.nextInt(height)
                );
            }
        }

        // Default to ghost's nearest vertex if we can't predict
        return ghost.nearestVertex();
    }

    /**
     * Gets the distance between two vertices using shortest path calculation
     */
    private int getDistance(MazeVertex v1, MazeVertex v2) {
        if (v1.equals(v2)) return 0;

        List<MazeEdge> path = Pathfinding.shortestNonBacktrackingPath(v1, v2, null);
        return (path == null) ? Integer.MAX_VALUE : path.size();
    }

    /**
     * Counts the number of dots remaining in the maze
     */
    private int countRemainingDots() {
        return (int) StreamSupport.stream(model.graph().vertices().spliterator(), false)
                .filter(v -> model.itemAt(v) == GameModel.Item.DOT)
                .count();
    }

    /**
     * Gets a list of all ghosts in the game
     */
    private List<Ghost> getGhosts() {
        return List.of(model.blinky(), model.pinky(), model.inky(), model.clyde());
    }

    /**
     * Represents a search node during path exploration
     */
    private static class SearchNode {
        final MazeEdge firstEdge;
        final MazeVertex vertex;
        final int depth;
        final Set<MazeVertex> visited;

        SearchNode(MazeEdge firstEdge, MazeVertex vertex, int depth, Set<MazeVertex> visited) {
            this.firstEdge = firstEdge;
            this.vertex = vertex;
            this.depth = depth;
            this.visited = visited;
        }
    }

    /**
     * Represents a potential path option with its score
     */
    private static class PathOption {
        final MazeEdge edge;
        final double score;
        final int depth;

        PathOption(MazeEdge edge, double score, int depth) {
            this.edge = edge;
            this.score = score;
            this.depth = depth;
        }
    }
}