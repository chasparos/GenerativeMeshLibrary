package com.planeguardian.assets.eval.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.planeguardian.assets.eval.GeometryEvaluatorApp;
import com.planeguardian.assets.generation.adapters.jme.JmeMeshAdapter;
import com.planeguardian.assets.generation.api.Vector3;
import com.planeguardian.assets.generation.geometry.eval.FaceGeometry;
import com.planeguardian.assets.generation.geometry.eval.SUTGeometryInterface;
import com.planeguardian.assets.generation.skeleton.GuideCurve;
import com.planeguardian.assets.generation.skeleton.GuideCurveSampler;
import com.planeguardian.assets.generation.skeleton.Plane;
import com.planeguardian.assets.generation.skeleton.Pole;
import com.planeguardian.assets.generation.skeleton.SkeletonEditOperations;
import com.planeguardian.assets.generation.skeleton.TopologicalSkeleton;
import com.planeguardian.assets.generation.skeleton.TopologyGenerator;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.triangulation.ProtoMeshTriangulator;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interactive curve editor for the authored {@link TopologicalSkeleton} of the Human Face SUT.
 * It expands the read-only "face inspector" overlay into a full editor with draggable handles,
 * driving all mutations through the plain-Java, unit-tested
 * {@link SkeletonEditOperations} helper (so this class stays a thin JME3 input/rendering shell).
 *
 * <p>By default only the curve/handle overlay is shown — no solid mesh is generated on every
 * edit, since regenerating and re-triangulating the full topology after each drag is far too slow
 * for interactive editing. The solid preview is opt-in via {@code [R]} and, once shown, is
 * dropped again on the next edit (kept in sync rather than silently going stale) until the user
 * explicitly regenerates it again.</p>
 *
 * <h2>Handles &amp; gestures</h2>
 * <ul>
 *   <li><b>Endpoint handles</b> (one sphere per pole with at least one curve): left-drag to move
 *       the pole, which re-anchors every connected curve. Poles on the symmetry plane are
 *       constrained to slide within it. A pole with only one curve "lights up" red — a dangling,
 *       not-yet-finished pole whose curve is excluded from the generated topology.</li>
 *   <li><b>Tangent handles</b> (a smaller sphere near each curve end): left-drag to shape the
 *       curve; the drag writes the curve's nearest interior control point (inserting one for a
 *       previously straight curve).</li>
 *   <li><b>[S] split</b> the selected curve at its midpoint, inserting a new draggable pole.</li>
 *   <li><b>[X] delete</b> the selected curve.</li>
 *   <li><b>[N] new curve</b> from the selected pole: then click a second pole to connect them.</li>
 *   <li><b>[D] reattach</b> the selected curve's end: then click the pole to reattach it to.</li>
 *   <li><b>[R] show/refresh</b> the opt-in solid preview from the current working skeleton.</li>
 *   <li><b>[L] preview level of detail</b>: cycles the solid preview between two densities
 *       (see {@link #PREVIEW_LOD_DELTA_T}), regenerating immediately if it is currently shown.</li>
 * </ul>
 *
 * <p>The orbit camera is never locked while editing: it stays fully responsive to scrolling to
 * zoom and (right-button-only, while the editor is active) dragging to orbit, so the left mouse
 * button is free for handle/curve interaction. See
 * {@link OrbitCameraAppState#setLeftClickRotateAllowed(boolean)}.</p>
 *
 * <p>Whenever the solid preview is (re)built, the working skeleton is validated by attempting to
 * build a generation skeleton; any {@code TopologyParityException}/{@code IllegalArgumentException}/
 * {@code TopologyGenerationException} is caught and surfaced in the HUD rather than crashing.</p>
 */
public final class CurveEditorAppState extends BaseAppState
        implements ActionListener, ViewerHudAppState.HudProvider {

    private static final int DISPLAY_SEGMENTS = 48;
    private static final float HANDLE_RADIUS = 0.02f;
    private static final float TANGENT_RADIUS = 0.013f;
    private static final float CURVE_PICK_RADIUS_PIXELS = 12f;

    /**
     * The two selectable solid-preview levels of detail, expressed as {@code deltaT}: the target
     * fraction of a curve's length spanned by each generated boundary segment (matching the
     * {@code HumanFaceSkeleton} authoring convention of {@code deltaT <= 0.1}). A curve's
     * {@code densitySegmentCount} for the preview is {@code round(1 / deltaT)}: 10 segments at
     * the fine 0.1 level, 4 at the coarse (much faster to regenerate) 0.25 level. This only
     * affects the opt-in preview mesh built by {@link #regeneratePreview()}; it never overwrites
     * the density actually authored on each {@link GuideCurve}.
     */
    private static final double[] PREVIEW_LOD_DELTA_T = {0.25, 0.1};

    private enum Pending { NONE, CREATE_CURVE, REATTACH_END }

    private enum DragKind { NONE, POLE, TANGENT }

    private final GeometryEvaluatorApp app;

    private SkeletonEditOperations working;
    private String editableGeneratorId;

    private Node editorNode;
    private Node curveNode;
    private Node handleNode;
    private Node tangentNode;
    private Geometry previewGeometry;
    private Material previewMaterial;

    private String selectedPoleId;
    private String selectedCurveId;
    private int previewLodIndex = 0;

    private Pending pending = Pending.NONE;
    private DragKind dragKind = DragKind.NONE;
    private String dragPoleId;
    private String dragTangentCurveId;
    private boolean dragTangentStartEnd;
    private final Vector3f dragPlaneNormal = new Vector3f();
    private final Vector3f dragPlanePoint = new Vector3f();

    private String status = "";

    public CurveEditorAppState(GeometryEvaluatorApp app) {
        this.app = app;
        setEnabled(false);
    }

    @Override
    protected void initialize(Application application) {
        // Scene/input are (re)built each time the mode is enabled; nothing global to set up here.
    }

    @Override
    protected void cleanup(Application application) {
    }

    @Override
    protected void onEnable() {
        SUTGeometryInterface generator = app.currentGenerator();
        if (generator instanceof FaceGeometry face) {
            working = new SkeletonEditOperations(face.skeleton());
            editableGeneratorId = generator.id();
            status = "Editing " + generator.displayName();
        } else {
            working = null;
            editableGeneratorId = null;
            status = "No editable skeleton for '" + (generator == null ? "-" : generator.displayName())
                    + "' (switch to Human Face with [6], then [E])";
        }

        editorNode = new Node("curve-editor");
        curveNode = new Node("curve-editor-curves");
        handleNode = new Node("curve-editor-pole-handles");
        tangentNode = new Node("curve-editor-tangent-handles");
        editorNode.attachChild(curveNode);
        editorNode.attachChild(handleNode);
        editorNode.attachChild(tangentNode);
        app.sceneRoot().attachChild(editorNode);

        previewMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Light/PBRLighting.j3md");
        previewMaterial.setColor("BaseColor", new ColorRGBA(0.55f, 0.6f, 0.7f, 1f));
        previewMaterial.setFloat("Roughness", 0.6f);
        previewMaterial.setFloat("Metallic", 0.05f);

        registerInput();
        // Curves-only by default: the full solid preview mesh is expensive to regenerate on
        // every edit, so it is opt-in via [R] rather than rebuilt automatically here.
        rebuildVisuals(false);
    }

    @Override
    protected void onDisable() {
        unregisterInput();
        if (editorNode != null) {
            editorNode.removeFromParent();
            editorNode = null;
        }
        selectedPoleId = null;
        selectedCurveId = null;
        pending = Pending.NONE;
        dragKind = DragKind.NONE;
    }

    // ---- Input ----------------------------------------------------------------------------

    private void registerInput() {
        InputManager input = app.getInputManager();
        addMapping(input, "EditorClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        addMapping(input, "EditorSplit", new KeyTrigger(KeyInput.KEY_S));
        addMapping(input, "EditorDelete", new KeyTrigger(KeyInput.KEY_X));
        addMapping(input, "EditorNewCurve", new KeyTrigger(KeyInput.KEY_N));
        addMapping(input, "EditorReattach", new KeyTrigger(KeyInput.KEY_D));
        addMapping(input, "EditorRegen", new KeyTrigger(KeyInput.KEY_R));
        addMapping(input, "EditorToggleLod", new KeyTrigger(KeyInput.KEY_L));
    }

    private void addMapping(InputManager input, String name, com.jme3.input.controls.Trigger trigger) {
        if (!input.hasMapping(name)) {
            input.addMapping(name, trigger);
        }
        input.addListener(this, name);
    }

    private void unregisterInput() {
        InputManager input = app.getInputManager();
        input.removeListener(this);
        for (String name : List.of("EditorClick", "EditorSplit", "EditorDelete", "EditorNewCurve", "EditorReattach", "EditorRegen", "EditorToggleLod")) {
            if (input.hasMapping(name)) input.deleteMapping(name);
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (working == null) return;
        switch (name) {
            case "EditorClick" -> onClick(isPressed);
            case "EditorSplit" -> { if (isPressed) splitSelectedCurve(); }
            case "EditorDelete" -> { if (isPressed) deleteSelectedCurve(); }
            case "EditorNewCurve" -> { if (isPressed) beginCreateCurve(); }
            case "EditorReattach" -> { if (isPressed) beginReattach(); }
            case "EditorRegen" -> { if (isPressed) rebuildVisuals(true); }
            case "EditorToggleLod" -> { if (isPressed) toggleLevelOfDetail(); }
            default -> { }
        }
    }

    private void onClick(boolean isPressed) {
        Vector2f cursor = app.getInputManager().getCursorPosition().clone();
        if (isPressed) {
            HandlePick pick = pickHandle(cursor);
            if (pick != null && pick.poleId != null) {
                onPoleClicked(pick.poleId, cursor);
            } else if (pick != null && pick.tangentCurveId != null) {
                beginTangentDrag(pick.tangentCurveId, pick.tangentStartEnd);
            } else {
                selectCurveAt(cursor);
            }
        } else {
            // Mouse released: end the drag. The curve/handle overlay already tracked the drag
            // live; the (opt-in) solid preview mesh is only rebuilt on an explicit [R].
            if (dragKind != DragKind.NONE) {
                dragKind = DragKind.NONE;
                dragPoleId = null;
                dragTangentCurveId = null;
                rebuildVisuals(false);
            }
        }
    }

    private void onPoleClicked(String poleId, Vector2f cursor) {
        switch (pending) {
            case CREATE_CURVE -> {
                if (selectedPoleId != null && !selectedPoleId.equals(poleId)) {
                    tryEdit(() -> working.createCurve(selectedPoleId, poleId, 1),
                            "Created curve " + selectedPoleId + " -> " + poleId);
                }
                pending = Pending.NONE;
                selectedPoleId = poleId;
                rebuildVisuals(false);
            }
            case REATTACH_END -> {
                if (selectedCurveId != null) {
                    String curveId = selectedCurveId;
                    tryEdit(() -> working.reattachEndpoint(curveId, false, poleId),
                            "Reattached " + curveId + " end -> " + poleId);
                }
                pending = Pending.NONE;
                rebuildVisuals(false);
            }
            default -> {
                selectedPoleId = poleId;
                selectedCurveId = null;
                beginPoleDrag(poleId, cursor);
                rebuildVisuals(false);
            }
        }
    }

    // ---- Dragging -------------------------------------------------------------------------

    private void beginPoleDrag(String poleId, Vector2f cursor) {
        dragKind = DragKind.POLE;
        dragPoleId = poleId;
        setupDragPlane(JmeMeshAdapter.toVector3f(working.pole(poleId).position()));
    }

    private void beginTangentDrag(String curveId, boolean startEnd) {
        dragKind = DragKind.TANGENT;
        dragTangentCurveId = curveId;
        dragTangentStartEnd = startEnd;
        selectedCurveId = curveId;
        setupDragPlane(tangentHandlePosition(working.curve(curveId), startEnd));
        rebuildVisuals(false);
    }

    private void setupDragPlane(Vector3f anchor) {
        Camera cam = app.getCamera();
        dragPlaneNormal.set(cam.getDirection()).normalizeLocal();
        dragPlanePoint.set(anchor);
    }

    @Override
    public void update(float tpf) {
        if (working == null || dragKind == DragKind.NONE) return;
        Vector2f cursor = app.getInputManager().getCursorPosition();
        Vector3f world = projectCursorToDragPlane(cursor);
        if (world == null) return;
        Vector3 position = new Vector3(world.x, world.y, world.z);
        if (dragKind == DragKind.POLE && dragPoleId != null) {
            working.movePole(dragPoleId, position);
        } else if (dragKind == DragKind.TANGENT && dragTangentCurveId != null) {
            applyTangentDrag(dragTangentCurveId, dragTangentStartEnd, position);
        }
        rebuildVisuals(false); // live geometry feedback; preview mesh is rebuilt on release
    }

    private Vector3f projectCursorToDragPlane(Vector2f cursor) {
        Camera cam = app.getCamera();
        Vector3f origin = cam.getWorldCoordinates(cursor, 0f);
        Vector3f far = cam.getWorldCoordinates(cursor, 1f);
        Ray ray = new Ray(origin, far.subtract(origin).normalizeLocal());
        com.jme3.math.Plane plane = new com.jme3.math.Plane(dragPlaneNormal, dragPlaneNormal.dot(dragPlanePoint));
        Vector3f contact = new Vector3f();
        return ray.intersectsWherePlane(plane, contact) ? contact : null;
    }

    /**
     * Writes the dragged tangent's world position onto the curve's nearest interior control point:
     * a single-control-point Catmull-Rom curve is enough to give it a shaped tangent, so a
     * previously straight curve gains one control point and an already-curved one has its first or
     * last control point moved.
     */
    private void applyTangentDrag(String curveId, boolean startEnd, Vector3 target) {
        GuideCurve curve = working.curve(curveId);
        List<Vector3> controlPoints = new ArrayList<>(curve.controlPoints());
        if (controlPoints.isEmpty()) {
            controlPoints.add(target);
        } else if (startEnd) {
            controlPoints.set(0, target);
        } else {
            controlPoints.set(controlPoints.size() - 1, target);
        }
        final List<Vector3> updated = controlPoints;
        tryEdit(() -> working.setControlPoints(curveId, updated), null);
    }

    // ---- Gesture commands -----------------------------------------------------------------

    private void splitSelectedCurve() {
        if (selectedCurveId == null) {
            status = "Select a curve first, then [S] to split it";
            return;
        }
        String curveId = selectedCurveId;
        Vector3 midpoint = working.curveMidpoint(curveId);
        String[] newPole = new String[1];
        boolean ok = tryEdit(() -> newPole[0] = working.splitCurve(curveId, midpoint),
                "Split " + curveId + " (drag the new pole to place it)");
        if (ok) {
            selectedPoleId = newPole[0];
            selectedCurveId = null;
        }
        rebuildVisuals(false);
    }

    private void deleteSelectedCurve() {
        if (selectedCurveId == null) {
            status = "Select a curve first, then [X] to delete it";
            return;
        }
        String curveId = selectedCurveId;
        tryEdit(() -> working.deleteCurve(curveId), "Deleted curve " + curveId);
        selectedCurveId = null;
        rebuildVisuals(false);
    }

    private void beginCreateCurve() {
        if (selectedPoleId == null) {
            status = "Select a pole first, then [N] and click a second pole";
            return;
        }
        pending = Pending.CREATE_CURVE;
        status = "New curve from " + selectedPoleId + ": click the target pole";
    }

    private void beginReattach() {
        if (selectedCurveId == null) {
            status = "Select a curve first, then [D] and click the pole to reattach its end to";
            return;
        }
        pending = Pending.REATTACH_END;
        status = "Reattach end of " + selectedCurveId + ": click the target pole";
    }

    /** Runs an edit, catching and surfacing any validation failure instead of letting it crash the app. */
    private boolean tryEdit(Runnable edit, String successMessage) {
        try {
            edit.run();
            if (successMessage != null) status = successMessage;
            return true;
        } catch (RuntimeException ex) {
            status = "Edit rejected: " + ex.getMessage();
            return false;
        }
    }

    // ---- Picking --------------------------------------------------------------------------

    private static final class HandlePick {
        String poleId;
        String tangentCurveId;
        boolean tangentStartEnd;
    }

    private HandlePick pickHandle(Vector2f cursor) {
        Camera cam = app.getCamera();
        Vector3f origin = cam.getWorldCoordinates(cursor, 0f);
        Vector3f far = cam.getWorldCoordinates(cursor, 1f);
        Ray ray = new Ray(origin, far.subtract(origin).normalizeLocal());

        CollisionResults results = new CollisionResults();
        handleNode.collideWith(ray, results);
        tangentNode.collideWith(ray, results);
        if (results.size() == 0) return null;
        CollisionResult closest = results.getClosestCollision();
        String name = closest.getGeometry().getName();
        return parseHandleName(name);
    }

    private HandlePick parseHandleName(String name) {
        if (name == null) return null;
        HandlePick pick = new HandlePick();
        if (name.startsWith("P|")) {
            pick.poleId = name.substring(2);
            return pick;
        }
        if (name.startsWith("T|")) {
            int lastBar = name.lastIndexOf('|');
            pick.tangentCurveId = name.substring(2, lastBar);
            pick.tangentStartEnd = name.endsWith("|S");
            return pick;
        }
        return null;
    }

    private void selectCurveAt(Vector2f cursor) {
        Camera cam = app.getCamera();
        String best = null;
        float bestDistance = CURVE_PICK_RADIUS_PIXELS;
        for (GuideCurve curve : working.curves()) {
            List<Vector3> polyline = sampleCurve(curve);
            for (int i = 0; i < polyline.size() - 1; i++) {
                Vector3f a3 = cam.getScreenCoordinates(JmeMeshAdapter.toVector3f(polyline.get(i)));
                Vector3f b3 = cam.getScreenCoordinates(JmeMeshAdapter.toVector3f(polyline.get(i + 1)));
                float distance = distancePointToSegment2D(cursor,
                        new Vector2f(a3.x, a3.y), new Vector2f(b3.x, b3.y));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = curve.id();
                }
            }
        }
        selectedCurveId = best;
        if (best != null) {
            selectedPoleId = null;
            status = "Selected curve " + best;
        }
        rebuildVisuals(false);
    }

    private static float distancePointToSegment2D(Vector2f point, Vector2f a, Vector2f b) {
        Vector2f ab = b.subtract(a);
        float lengthSquared = ab.lengthSquared();
        float t = lengthSquared <= 1.0e-12f ? 0f : point.subtract(a).dot(ab) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        return point.distance(a.add(ab.mult(t)));
    }

    // ---- Visuals --------------------------------------------------------------------------

    private void rebuildVisuals(boolean regeneratePreview) {
        if (working == null || editorNode == null) return;
        curveNode.detachAllChildren();
        handleNode.detachAllChildren();
        tangentNode.detachAllChildren();

        Set<String> litPoles = working.singleCurvePoleIds();

        for (GuideCurve curve : working.curves()) {
            List<Vector3> polyline = sampleCurve(curve);
            boolean selected = curve.id().equals(selectedCurveId);
            Geometry line = new Geometry("edit-curve-" + curve.id(),
                    JmeMeshAdapter.toPolylineMesh(List.of(polyline)));
            Material material = lineMaterial(selected
                    ? new ColorRGBA(1f, 0.85f, 0.1f, 1f)
                    : new ColorRGBA(0.2f, 0.75f, 1f, 1f), selected ? 5f : 2f);
            line.setMaterial(material);
            curveNode.attachChild(line);

            // Tangent handles near each end.
            attachTangentHandle(curve, true);
            attachTangentHandle(curve, false);
        }

        for (Pole pole : working.poles().values()) {
            if (working.degree(pole.id()) < 1) continue;
            boolean lit = litPoles.contains(pole.id());
            boolean selected = pole.id().equals(selectedPoleId);
            ColorRGBA color = lit ? new ColorRGBA(1f, 0.15f, 0.15f, 1f)
                    : selected ? new ColorRGBA(1f, 0.9f, 0.2f, 1f)
                    : pole.isOnSymmetryPlane() ? new ColorRGBA(0.4f, 1f, 0.5f, 1f)
                    : new ColorRGBA(0.85f, 0.85f, 0.9f, 1f);
            Geometry handle = sphere("P|" + pole.id(), HANDLE_RADIUS, color);
            handle.setLocalTranslation(JmeMeshAdapter.toVector3f(pole.position()));
            handleNode.attachChild(handle);
        }

        if (regeneratePreview) {
            regeneratePreview();
        } else if (previewGeometry != null) {
            // The overlay only ever shows curves/handles by default (the solid mesh is opt-in
            // and expensive to keep in sync); drop the now-stale preview from a prior [R] rather
            // than let it silently diverge from the curves being edited.
            previewGeometry.removeFromParent();
            previewGeometry = null;
        }
    }

    private void attachTangentHandle(GuideCurve curve, boolean startEnd) {
        Vector3f position = tangentHandlePosition(curve, startEnd);
        Geometry handle = sphere("T|" + curve.id() + "|" + (startEnd ? "S" : "E"),
                TANGENT_RADIUS, new ColorRGBA(1f, 0.5f, 0.9f, 1f));
        handle.setLocalTranslation(position);
        tangentNode.attachChild(handle);
    }

    /**
     * The world position of a curve's tangent handle at the given end: its first/last authored
     * control point if present, otherwise a point a short way along the straight chord toward the
     * other endpoint (so a straight curve still shows a grabbable tangent handle).
     */
    private Vector3f tangentHandlePosition(GuideCurve curve, boolean startEnd) {
        List<Vector3> controlPoints = curve.controlPoints();
        if (!controlPoints.isEmpty()) {
            Vector3 point = startEnd ? controlPoints.get(0) : controlPoints.get(controlPoints.size() - 1);
            return JmeMeshAdapter.toVector3f(point);
        }
        Vector3 start = working.pole(curve.startPoleId()).position();
        Vector3 end = working.pole(curve.endPoleId()).position();
        Vector3 from = startEnd ? start : end;
        Vector3 to = startEnd ? end : start;
        double t = 0.25;
        return new Vector3f(
                (float) (from.x() + (to.x() - from.x()) * t),
                (float) (from.y() + (to.y() - from.y()) * t),
                (float) (from.z() + (to.z() - from.z()) * t));
    }

    private List<Vector3> sampleCurve(GuideCurve curve) {
        Pole start = working.pole(curve.startPoleId());
        Pole end = working.pole(curve.endPoleId());
        Plane seam = working.isSeamCurve(curve) ? working.symmetryPlane() : null;
        return GuideCurveSampler.sampleForDisplay(curve, start.position(), end.position(), seam, DISPLAY_SEGMENTS);
    }

    private void regeneratePreview() {
        if (previewGeometry != null) {
            previewGeometry.removeFromParent();
            previewGeometry = null;
        }
        try {
            TopologicalSkeleton skeleton = applyPreviewLod(working.toGenerationSkeleton());
            ProtoMeshSnapshot mesh = new TopologyGenerator().generate(skeleton).mesh();
            if (!mesh.isValid()) {
                status = "Preview invalid: " + mesh.issues();
                return;
            }
            TriangulatedMesh triangulated = ProtoMeshTriangulator.triangulate(mesh);
            JmeMeshAdapter.TriangleMeshResult result = JmeMeshAdapter.toTriangleMesh(triangulated);
            previewGeometry = new Geometry("edit-preview", result.mesh());
            previewGeometry.setMaterial(previewMaterial);
            previewGeometry.setShadowMode(RenderQueue.ShadowMode.Off);
            editorNode.attachChild(previewGeometry);
            status = "Preview regenerated at LOD " + PREVIEW_LOD_DELTA_T[previewLodIndex];
        } catch (RuntimeException ex) {
            status = "Preview unavailable: " + ex.getMessage();
        }
    }

    /**
     * Returns a copy of {@code skeleton} with every curve's {@code densitySegmentCount}
     * overridden to a single uniform value derived from the current preview LOD (see
     * {@link #PREVIEW_LOD_DELTA_T}), regardless of each curve's individually authored density.
     * Because every curve gets the same value, any two opposite sides of a four-sided patch stay
     * equal (a {@link TopologyGenerator} requirement) automatically, and the usual odd-sum parity
     * repair still runs normally on top of this. This never mutates the editor's own working
     * skeleton — only the transient mesh built for the on-screen preview.
     */
    private TopologicalSkeleton applyPreviewLod(TopologicalSkeleton skeleton) {
        int segments = Math.max(1, (int) Math.round(1.0 / PREVIEW_LOD_DELTA_T[previewLodIndex]));
        List<GuideCurve> scaled = new ArrayList<>(skeleton.curves().size());
        for (GuideCurve curve : skeleton.curves()) {
            scaled.add(curve.withDensitySegmentCount(segments));
        }
        return new TopologicalSkeleton(skeleton.poles(), scaled, skeleton.isMirrored(), skeleton.symmetryPlane(),
                skeleton.holeCurveIds(), skeleton.ringInsetCurveIds());
    }

    /** Cycles the solid preview's level of detail (key [L]) and regenerates it if currently shown. */
    private void toggleLevelOfDetail() {
        previewLodIndex = (previewLodIndex + 1) % PREVIEW_LOD_DELTA_T.length;
        if (previewGeometry != null) {
            regeneratePreview();
        } else {
            status = "Preview LOD set to " + PREVIEW_LOD_DELTA_T[previewLodIndex] + " (press [R] to preview)";
        }
    }

    private Geometry sphere(String name, float radius, ColorRGBA color) {
        Geometry geometry = new Geometry(name, new Sphere(10, 10, radius));
        Material material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        material.getAdditionalRenderState().setDepthTest(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geometry;
    }

    private Material lineMaterial(ColorRGBA color, float width) {
        Material material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        material.getAdditionalRenderState().setLineWidth(width);
        material.getAdditionalRenderState().setDepthTest(false);
        return material;
    }

    // ---- HUD ------------------------------------------------------------------------------

    @Override
    public List<String> hudLines() {
        if (!isEnabled()) return List.of();
        if (working == null) {
            return List.of("-- Curve Editor --", status);
        }
        return List.of(
                "-- Curve Editor (drag handles; right-drag/scroll still orbit camera) --",
                "Left-drag pole handle: move pole (on-plane poles slide in x=0)",
                "Left-drag pink handle: shape curve tangent",
                "[S] split selected curve   [X] delete selected curve",
                "[N] new curve from selected pole   [D] reattach selected curve end",
                "[R] show/refresh solid preview (curves-only otherwise)   [L] preview LOD: "
                        + PREVIEW_LOD_DELTA_T[previewLodIndex] + "   [E] exit editor",
                "Selected pole: " + (selectedPoleId == null ? "-" : selectedPoleId)
                        + "   Selected curve: " + (selectedCurveId == null ? "-" : selectedCurveId),
                "Lit (single-curve, excluded) poles: " + working.singleCurvePoleIds().size()
                        + (pending == Pending.NONE ? "" : "   Pending: " + pending),
                status);
    }
}
