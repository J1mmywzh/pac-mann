package ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeListener;
import javax.swing.Timer;
import javax.swing.event.SwingPropertyChangeSupport;
import model.GameModel;
import model.MazeGraph.Direction;

public class InteractiveGameController implements KeyListener {

    public enum GameState {RUNNING, PAUSED, LIFESTART, GAMEOVER}

    private GameModel model;
    private final Timer timer;
    private GameState state;

    /**
     * Helper object for managing property change notifications.
     */
    protected SwingPropertyChangeSupport propSupport;

    public InteractiveGameController(GameModel model) {
        state = GameState.LIFESTART;
        timer = new Timer(16, e -> nextFrame());

        boolean notifyOnEdt = true;
        propSupport = new SwingPropertyChangeSupport(this, notifyOnEdt);

        setModel(model);
    }

    public void setModel(GameModel newModel) {
        reset();
        model = newModel;
        model.addPropertyChangeListener("game_state", e -> {
            if (model.state() != GameModel.GameState.PLAYING) {
                stopGame();
            }
        });
    }

    private void stopGame() {
        timer.stop();
        setState(model.state() == GameModel.GameState.READY ? GameState.LIFESTART
                : GameState.GAMEOVER);
    }

    private void nextFrame() {
        // TODO: duration?
        model.updateActors(16);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Do nothing
    }

    @Override
    public void keyPressed(KeyEvent e) {
        Direction direction = null;

        // Map key codes to directions
        switch (e.getKeyCode()) {

            // Left movement
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                direction = Direction.LEFT;
                break;

            // Right movement
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                direction = Direction.RIGHT;
                break;

            // Up movement
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                direction = Direction.UP;
                break;

            // Down movement
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                direction = Direction.DOWN;
                break;

            // Start/Pause
            case KeyEvent.VK_SPACE:
                // Space has same effect as start/pause button
                processStartPause();
                return;
        }

        // Handles all direction commands
        if (direction != null) {

            // Starts game if not already running
            if (state == GameState.PAUSED || state == GameState.LIFESTART) {
                processStartPause();
            }

            // Updates  model with new direction
            model.updatePlayerCommand(direction);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Do nothing
    }

    /**
     * Processes a press of the start/pause button. Toggles between the RUNNING and PAUSED
     * GameStates.
     */
    public void processStartPause() {
        if (state == GameState.PAUSED) {
            setState(GameState.RUNNING);
            timer.start();
        } else if (state == GameState.RUNNING) {
            timer.stop();
            setState(GameState.PAUSED);
        } else if (state == GameState.LIFESTART) {
//            model.useLife();
            setState(GameState.RUNNING);
            timer.start();
        }
    }

    public void pause() {
        if (state == GameState.RUNNING) {
            timer.stop();
            setState(GameState.PAUSED);
        }
    }

    public void reset() {
        timer.stop();
        setState(GameState.LIFESTART);
    }

    public GameState state() {
        return state;
    }

    private void setState(GameState newState) {
        GameState oldState = state;
        state = newState;
        propSupport.firePropertyChange("game_state", oldState, state);
    }

    /* Observation interface */

    /**
     * Register `listener` to be notified whenever any property of this model is changed.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    /**
     * Register `listener` to be notified whenever the property named `propertyName` of this model
     * is changed.
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Stop notifying `listener` of property changes for this model (assuming it was added no more
     * than once).  Does not affect listeners who were registered with a particular property name.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    /**
     * Stop notifying `listener` of changes to the property named `propertyName` for this model
     * (assuming it was added no more than once).  Does not affect listeners who were not registered
     * with `propertyName`.
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(propertyName, listener);
    }
}
