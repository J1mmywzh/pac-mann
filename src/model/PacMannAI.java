package model.ai;

import java.util.*;
import java.util.stream.*;
import graph.Pathfinding;
import model.GameModel;
import model.GameModel.Item;
import model.MazeGraph.IPair;
import model.MazeGraph.MazeEdge;
import model.MazeGraph.MazeVertex;
import model.MazeGraph.Direction;
import model.Blinky;
import model.Pinky;
import model.Inky;
import model.Clyde;
import model.Ghost;
import model.Ghost.GhostState;
import model.PacMann;

/**
 * PacMannAI with best-first search (A*-style), potential-field avoidance,
 * strategic pellet use, and ghost-specific corner exploits.
 */
public class PacMannAI extends PacMann {
    private static final int    SEARCH_DEPTH               = 8;
    private static final double DOT_SCORE                 = 10.0;
    private static final double PELLET_SCORE              = 50.0;
    private static final double GHOST_EAT_SCORE           = 200.0;
    private static final double GHOST_COLLISION_PENALTY   = -1000.0;
    private static final double GHOST_POTENTIAL_WEIGHT    = 300.0;
    private static final int    GHOST_EVADE_RADIUS        = 2;
    private static final int    PELLET_RADIUS             = 6;
    private static final int    PELLET_GHOST_THRESHOLD    = 2;
    private static final int    DOT_REMAIN_THRESHOLD      = 20;

    public PacMannAI(GameModel model) {
        super(model);
    }

    @Override
    public MazeEdge nextEdge() {
        MazeVertex start = nearestVertex();
        MazeEdge prev = location().edge();
        List<Ghost> ghosts = List.of(model.blinky(), model.pinky(), model.inky(), model.clyde());
        boolean evasion = isEvasionMode(start, ghosts);
        boolean pelletMode = shouldEatPellet(start, ghosts);

        // Junction exploit: random at true junction
        List<MazeEdge> exits = new ArrayList<>();
        for (MazeEdge e: start.outgoingEdges()) {
            if (prev == null || !e.dst().equals(prev.src())) exits.add(e);
        }
        if (exits.size() >= 3) {
            return exits.get(new Random((long)model.time()).nextInt(exits.size()));
        }

        // A*-style frontier
        PriorityQueue<Node> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(n -> -(n.score + heuristic(n.vertex, ghosts))));
        // seed frontier
        for (MazeEdge e: start.outgoingEdges()) {
            if (prev != null && e.dst().equals(prev.src())) continue;
            double sc = scoreVertex(e.dst(), ghosts, evasion, pelletMode) - e.weight();
            frontier.add(new Node(e, e.dst(), 1, sc, Set.of(start)));
        }

        Node best = null;
        while (!frontier.isEmpty()) {
            Node cur = frontier.poll();
            if (best == null || cur.score > best.score) best = cur;
            if (cur.depth >= SEARCH_DEPTH) continue;
            for (MazeEdge e: cur.vertex.outgoingEdges()) {
                if (e.dst().equals(cur.firstEdge.src()) && cur.vertex.outgoingEdges().size() > 1) continue;
                if (cur.visited.contains(e.dst())) continue;
                double sc = cur.score + scoreVertex(e.dst(), ghosts, evasion, pelletMode) - e.weight();
                Set<MazeVertex> vis = new HashSet<>(cur.visited);
                vis.add(e.dst());
                frontier.add(new Node(cur.firstEdge, e.dst(), cur.depth+1, sc, vis));
            }
        }
        return best != null ? best.firstEdge : ((start.outgoingEdges().iterator().hasNext()) ? start.outgoingEdges().iterator().next() : prev);
    }

    private boolean isEvasionMode(MazeVertex cur, List<Ghost> ghosts) {
        for (Ghost g: ghosts) {
            if (g.state() != GhostState.FLEE) {
                List<MazeEdge> p = Pathfinding.shortestNonBacktrackingPath(cur, g.nearestVertex(), null);
                if (p != null && p.size() <= GHOST_EVADE_RADIUS) return true;
            }
        }
        return false;
    }

    private boolean shouldEatPellet(MazeVertex cur, List<Ghost> ghosts) {
        int dots = (int) StreamSupport.stream(model.graph().vertices().spliterator(), false)
                .filter(v -> model.itemAt(v) == Item.DOT).count();
        long near = ghosts.stream().filter(g -> {
            List<MazeEdge> p = Pathfinding.shortestNonBacktrackingPath(cur, g.nearestVertex(), null);
            return p != null && p.size() <= PELLET_RADIUS;
        }).count();
        return near >= PELLET_GHOST_THRESHOLD || dots < DOT_REMAIN_THRESHOLD;
    }

    private double scoreVertex(MazeVertex v, List<Ghost> ghosts,
            boolean evasion, boolean pelletMode) {
        double score = 0;
        Item it = model.itemAt(v);
        if (it == Item.DOT) score += DOT_SCORE;
        if (it == Item.PELLET && pelletMode) score += PELLET_SCORE;
        for (Ghost g: ghosts) {
            if (g.state() == GhostState.FLEE && pelletMode && v.equals(g.nearestVertex())) {
                score += GHOST_EAT_SCORE; // ghost-eating bonus
            } else if (g.state() != GhostState.FLEE) {
                List<MazeEdge> p = Pathfinding.shortestNonBacktrackingPath(v, g.nearestVertex(), null);
                if (p != null) {
                    int d = p.size();
                    // collision
                    if (d == 0) return GHOST_COLLISION_PENALTY;
                    // potential-field avoidance
                    score -= GHOST_POTENTIAL_WEIGHT / (d*d + 1);
                    // evasion bonus if already in threat
                    if (evasion && d <= GHOST_EVADE_RADIUS) score += GHOST_COLLISION_PENALTY/2;
                }
            }
        }
        return score;
    }

    private double heuristic(MazeVertex v, List<Ghost> ghosts) {
        double h = 0;
        for (Ghost g: ghosts) {
            if (g.state() != GhostState.FLEE) {
                List<MazeEdge> p = Pathfinding.shortestNonBacktrackingPath(v, g.nearestVertex(), null);
                int d = (p == null ? SEARCH_DEPTH : p.size());
                h += 1.0/(d+1);
            }
        }
        return h;
    }

    private MazeVertex ghostTarget(Ghost g, MazeVertex pac) {
        int w = model.width(), h = model.height();
        if (g instanceof Blinky) {
            return g.state()==GhostState.CHASE ? pac : model.graph().closestTo(2,2);
        }
        if (g instanceof Pinky) {
            if (g.state()==GhostState.CHASE) {
                MazeEdge e = location().edge();
                if (e!=null) {
                    Direction d = e.direction();
                    int di=0,dj=0;
                    switch(d){case LEFT:di=-3;break;case RIGHT:di=3;break;
                        case UP:dj=-3;break;case DOWN:dj=3;break;}
                    return model.graph().closestTo(pac.loc().i()+di,pac.loc().j()+dj);
                }
            }
            return model.graph().closestTo(w-3,2);
        }
        if (g instanceof Inky) {
            if (g.state()==GhostState.CHASE) {
                IPair bl= model.blinky().nearestVertex().loc();
                IPair pp= pac.loc();
                return model.graph().closestTo(2*pp.i()-bl.i(),2*pp.j()-bl.j());
            }
            return model.graph().closestTo(2,h-3);
        }
        if (g instanceof Clyde) {
            if (g.state()==GhostState.CHASE) {
                IPair pp= pac.loc(); IPair cc= g.nearestVertex().loc();
                if (Math.hypot(pp.i()-cc.i(),pp.j()-cc.j())>=10) return pac;
                return model.graph().closestTo(new Random().nextInt(w),new Random().nextInt(h));
            }
            return model.graph().closestTo(w-3,h-3);
        }
        List<MazeEdge> gp=g.guidancePath();
        return gp.isEmpty()?g.nearestVertex():gp.get(gp.size()-1).dst();
    }

    private static class Node {
        final MazeEdge firstEdge;
        final MazeVertex vertex;
        final int depth;
        final double score;
        final Set<MazeVertex> visited;
        Node(MazeEdge fe, MazeVertex v, int d, double s, Set<MazeVertex> vis){
            firstEdge=fe;vertex=v;depth=d;score=s;visited=vis;
        }
    }
}