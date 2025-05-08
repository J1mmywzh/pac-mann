package model.ai;

import java.util.*;
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
 * Enhanced AI for PacMann with ghost-specific avoidance and scoring.
 */
public class PacMannAI extends PacMann {
    private static final double DOT_REWARD    = 10.0;
    private static final double PELLET_REWARD = 50.0;
    private static final double GHOST_BONUS   = 200.0;
    private static final double GHOST_PENALTY = -1000.0;
    private static final int    MAX_DEPTH     = 10;
    private static final int    THREAT_STEPS  = 3;

    public PacMannAI(GameModel model) {
        super(model);
    }

    @Override
    public MazeEdge nextEdge() {
        MazeVertex cur = nearestVertex();
        MazeEdge prev = location().edge();

        // 1. Build move list without backtracking
        List<MazeEdge> moves = new ArrayList<>();
        for (MazeEdge e : cur.outgoingEdges()) {
            if (prev != null && e.dst().equals(prev.src())) continue;
            moves.add(e);
        }
        if (moves.isEmpty()) {
            for (MazeEdge e : cur.outgoingEdges()) {
                moves.add(e);
            }
        }

        // 2. Filter moves colliding with a live ghost next step
        List<MazeEdge> safe = new ArrayList<>();
        for (MazeEdge e : moves) {
            boolean collide = false;
            for (Ghost g : List.of(model.blinky(), model.pinky(), model.inky(), model.clyde())) {
                if (g.state() != GhostState.FLEE) {
                    MazeVertex nextPos = predictGhostPos(g, 1);
                    if (e.dst().equals(nextPos)) {
                        collide = true;
                        break;
                    }
                }
            }
            if (!collide) safe.add(e);
        }
        if (!safe.isEmpty()) moves = safe;

        // 3. Pellet strategy: only eat when jammed and target ghosts
        if (detectJam(cur)) {
            double bestScore = Double.POSITIVE_INFINITY;
            MazeEdge bestPellet = null;
            for (MazeEdge e : moves) {
                if (model.itemAt(e.dst()) == Item.PELLET) {
                    double sumDist = 0;
                    for (Ghost g : List.of(model.blinky(), model.pinky(), model.inky(), model.clyde())) {
                        List<MazeEdge> p = Pathfinding.shortestNonBacktrackingPath(
                                e.dst(), g.nearestVertex(), null);
                        sumDist += (p == null ? MAX_DEPTH : p.size());
                    }
                    if (sumDist < bestScore) {
                        bestScore = sumDist;
                        bestPellet = e;
                    }
                }
            }
            if (bestPellet != null) {
                return bestPellet;
            }
        }

        // 4. Chase edible ghosts immediately Chase edible ghosts immediately
        for (Ghost g : List.of(model.blinky(), model.pinky(), model.inky(), model.clyde())) {
            if (g.state() == GhostState.FLEE) {
                MazeEdge chase = chaseGhost(cur, prev, g);
                if (chase != null) {
                    return chase;
                }
            }
        }

        // 5. Recursive scoring for best move
        MazeEdge best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MazeEdge e : moves) {
            double sc = scoreRoute(e.dst(), e, MAX_DEPTH);
            if (sc > bestScore) {
                bestScore = sc;
                best = e;
            }
        }
        return best != null ? best : moves.get(0);
    }

    /**
     * Detect if 2+ ghosts are within THREAT_STEPS via path length
     */
    private boolean detectJam(MazeVertex cur) {
        int count = 0;
        for (Ghost g : List.of(model.blinky(), model.pinky(), model.inky(), model.clyde())) {
            List<MazeEdge> path = Pathfinding.shortestNonBacktrackingPath(cur, g.nearestVertex(), null);
            if (path != null && path.size() - 1 <= THREAT_STEPS) {
                count++;
            }
        }
        return count >= 2;
    }

    /**
     * Chase a given ghost by one edge toward its target
     */
    private MazeEdge chaseGhost(MazeVertex from, MazeEdge back, Ghost g) {
        MazeVertex tgt = ghostTarget(g, from);
        List<MazeEdge> path = Pathfinding.shortestNonBacktrackingPath(from, tgt, back);
        return (path != null && !path.isEmpty()) ? path.get(0) : null;
    }

    /**
     * Determine each ghost's target based on its subclass and state
     */
    private MazeVertex ghostTarget(Ghost g, MazeVertex pacPos) {
        int w = model.width();
        int h = model.height();
        if (g instanceof Blinky) {
            return g.state() == GhostState.CHASE
                    ? pacPos
                    : model.graph().closestTo(2, 2);
        }
        if (g instanceof Pinky) {
            if (g.state() == GhostState.CHASE) {
                MazeEdge curr = location().edge();
                if (curr != null) {
                    Direction d = curr.direction();
                    int di = 0, dj = 0;
                    switch (d) {
                        case LEFT:  di = -3; break;
                        case RIGHT: di =  3; break;
                        case UP:    dj = -3; break;
                        case DOWN:  dj =  3; break;
                    }
                    return model.graph().closestTo(pacPos.loc().i() + di,
                            pacPos.loc().j() + dj);
                }
                return pacPos;
            }
            return model.graph().closestTo(w - 3, 2);
        }
        if (g instanceof Inky) {
            if (g.state() == GhostState.CHASE) {
                MazeVertex bl = model.blinky().nearestVertex();
                int mi = (pacPos.loc().i() + bl.loc().i()) / 2;
                int mj = (pacPos.loc().j() + bl.loc().j()) / 2;
                return model.graph().closestTo(mi, mj);
            }
            return model.graph().closestTo(2, h - 3);
        }
        if (g instanceof Clyde) {
            if (g.state() == GhostState.CHASE) {
                double dx = pacPos.loc().i() - g.nearestVertex().loc().i();
                double dy = pacPos.loc().j() - g.nearestVertex().loc().j();
                if (Math.hypot(dx, dy) >= 10) {
                    return pacPos;
                } else {
                    Random r = new Random();
                    return model.graph().closestTo(r.nextInt(w), r.nextInt(h));
                }
            }
            return model.graph().closestTo(w - 3, h - 3);
        }
        List<MazeEdge> gp = g.guidancePath();
        return gp.isEmpty() ? g.nearestVertex() : gp.get(gp.size() - 1).dst();
    }

    /**
     * Recursively score a route with collision prediction
     */
    private double scoreRoute(MazeVertex v, MazeEdge in, int depth) {
        if (depth <= 0) return 0;
        double score = 0;
        Item it = model.itemAt(v);
        if (it == Item.DOT) score += DOT_REWARD;
        else if (it == Item.PELLET) score += PELLET_REWARD + GHOST_BONUS;
        for (Ghost g : List.of(model.blinky(), model.pinky(), model.inky(), model.clyde())) {
            MazeVertex gpos = predictGhostPos(g, depth);
            if (v.equals(gpos)) return GHOST_PENALTY;
        }
        double bestF = -Double.MAX_VALUE;
        for (MazeEdge e : v.outgoingEdges()) {
            if (in != null && e.dst().equals(in.src())) continue;
            double val = scoreRoute(e.dst(), e, depth - 1) - cost(e);
            bestF = Math.max(bestF, val);
        }
        return score + Math.max(bestF, 0);
    }

    /**
     * Predict ghost position k steps ahead following its chase logic
     */
    private MazeVertex predictGhostPos(Ghost g, int depth) {
        MazeVertex pos = g.nearestVertex();
        if (depth <= 0) return pos;
        MazeEdge next = chaseGhost(pos, null, g);
        return next == null ? pos : predictGhostPos(g, depth - 1);
    }

    /**
     * Travel cost with elevation adjustment
     */
    private double cost(MazeEdge e) {
        double base = e.weight();
        IPair s = e.src().loc();
        IPair d = e.dst().loc();
        double se = model.map().elevations()[s.i()][s.j()];
        double de = model.map().elevations()[d.i()][d.j()];
        return base + (de - se);
    }
}
