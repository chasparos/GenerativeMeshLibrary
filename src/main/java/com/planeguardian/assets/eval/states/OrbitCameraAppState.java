package com.planeguardian.assets.eval.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.ChaseCamera;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.controls.Trigger;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.List;

/**
 * Encapsulates the viewer's orbit camera: a JME3 {@link ChaseCamera} locked onto a movable
 * target node, driven by mouse-drag orbit and scroll zoom. Extracting it into a dedicated
 * {@code AppState} keeps all camera lifecycle and framing math out of the main application.
 *
 * <p>The camera's target and distance are set through {@link #frame(List)}, which fits the
 * supplied world-space points into view — used both for "frame all" and "frame selection".</p>
 */
public final class OrbitCameraAppState extends BaseAppState {

    /** Default: either mouse button drags to orbit (matches {@link ChaseCamera}'s own default). */
    private static final Trigger[] BOTH_BUTTONS_ROTATE = {
            new MouseButtonTrigger(MouseInput.BUTTON_LEFT),
            new MouseButtonTrigger(MouseInput.BUTTON_RIGHT)
    };

    /** Restricted: only the right mouse button orbits, freeing the left button for other tools. */
    private static final Trigger[] RIGHT_BUTTON_ONLY_ROTATE = {
            new MouseButtonTrigger(MouseInput.BUTTON_RIGHT)
    };

    private final Node cameraTarget = new Node("camera-target");
    private ChaseCamera chaseCam;
    private List<Vector3f> pendingFrame;
    private Boolean pendingLeftClickRotateAllowed;

    @Override
    protected void initialize(Application app) {
        SimpleApplication simpleApp = (SimpleApplication) app;
        simpleApp.getRootNode().attachChild(cameraTarget);
        chaseCam = new ChaseCamera(app.getCamera(), cameraTarget, simpleApp.getInputManager());
        chaseCam.setDragToRotate(true);
        chaseCam.setDefaultVerticalRotation(FastMath.PI / 6f);
        chaseCam.setMinDistance(0.1f);
        chaseCam.setMaxDistance(500f);
        chaseCam.setZoomSensitivity(2f);
        // BaseAppState only calls initialize() lazily, on the first update() after attach(),
        // not synchronously from stateManager.attach(...); an eager frame(...) call made right
        // after attaching this state (e.g. an initial "frame all" in simpleInitApp) would race
        // ahead of chaseCam's construction and NPE. Replay it now that the camera actually exists.
        if (pendingFrame != null) {
            List<Vector3f> points = pendingFrame;
            pendingFrame = null;
            frame(points);
        }
        if (pendingLeftClickRotateAllowed != null) {
            boolean allowed = pendingLeftClickRotateAllowed;
            pendingLeftClickRotateAllowed = null;
            setLeftClickRotateAllowed(allowed);
        }
    }

    @Override
    protected void cleanup(Application app) {
        cameraTarget.removeFromParent();
    }

    @Override
    protected void onEnable() {
        if (chaseCam != null) chaseCam.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        if (chaseCam != null) chaseCam.setEnabled(false);
    }

    /**
     * Restricts orbiting to the right mouse button only (freeing the left button for another
     * tool, such as the curve editor's handle dragging) when {@code allowed} is {@code false};
     * restores the default both-buttons-orbit behaviour when {@code true}. The camera itself
     * stays fully enabled either way — only the mouse button(s) that trigger rotation change, so
     * scrolling to zoom and right-drag orbiting always keep working.
     */
    public void setLeftClickRotateAllowed(boolean allowed) {
        if (chaseCam == null) {
            // Not yet initialized (see #initialize) — remember and replay once it is.
            pendingLeftClickRotateAllowed = allowed;
            return;
        }
        chaseCam.setToggleRotationTrigger(allowed ? BOTH_BUTTONS_ROTATE : RIGHT_BUTTON_ONLY_ROTATE);
    }

    /** Fits {@code points} (world space) into view, centring and pulling the camera back to frame them. */
    public void frame(List<Vector3f> points) {
        if (points == null || points.isEmpty()) return;
        if (chaseCam == null) {
            // Not yet initialized (see #initialize) — remember and replay once it is.
            pendingFrame = points;
            return;
        }
        Vector3f min = points.get(0).clone();
        Vector3f max = points.get(0).clone();
        for (Vector3f point : points) {
            min.minLocal(point);
            max.maxLocal(point);
        }
        Vector3f center = min.add(max).multLocal(0.5f);
        float radius = Math.max(0.25f, center.distance(max));

        cameraTarget.setLocalTranslation(center);
        chaseCam.setDefaultDistance(radius * 3f);
        chaseCam.setMinDistance(Math.max(0.05f, radius * 0.15f));
        chaseCam.setMaxDistance(Math.max(200f, radius * 20f));
        // ChaseCamera reads its distance lazily from the default on the next update;
        // nudge the camera immediately so framing feels instantaneous.
        getApplication().getCamera().setLocation(center.add(0, radius * 0.6f, radius * 3f));
        getApplication().getCamera().lookAt(center, Vector3f.UNIT_Y);
    }
}
