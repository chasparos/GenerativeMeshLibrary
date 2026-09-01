package com.planeguardian.assets.eval;

import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.ChaseCamera;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import com.jme3.util.BufferUtils;
import com.planeguardian.assets.generation.adapters.jme.JmeMeshAdapter;
import com.planeguardian.assets.generation.geometry.eval.SUTGeometryInterface;
import com.planeguardian.assets.generation.geometry.eval.TubeGeometry;
import com.planeguardian.assets.generation.topology.EdgeId;
import com.planeguardian.assets.generation.topology.FaceId;
import com.planeguardian.assets.generation.topology.LoopId;
import com.planeguardian.assets.generation.topology.ProtoEdge;
import com.planeguardian.assets.generation.topology.ProtoFace;
import com.planeguardian.assets.generation.topology.ProtoLoop;
import com.planeguardian.assets.generation.topology.ProtoMeshSnapshot;
import com.planeguardian.assets.generation.topology.ProtoVertex;
import com.planeguardian.assets.generation.topology.VertexId;
import com.planeguardian.assets.generation.triangulation.ProtoMeshTriangulator;
import com.planeguardian.assets.generation.triangulation.TriangulatedMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic JME3 evaluation modeler used to exercise the geometry tools in this
 * library. Displays a {@link SUTGeometryInterface} implementation (a
 * {@link TubeGeometry} by default) with an orbit camera, a shaded/wireframe
 * toggle, three-point lighting, a red-clay PBR ground plane at {@code y=0},
 * an "edge mesh" overlay toggle, face/edge selection, and camera framing.
 */
public final class GeometryEvaluatorApp extends SimpleApplication implements ActionListener {

    private static final float CLICK_DRAG_THRESHOLD_PIXELS = 4f;

    private enum SelectMode { FACE, EDGE }

    private Node meshNode;
    private Node cameraTargetNode;
    private ChaseCamera chaseCam;

    private Geometry solidGeometry;
    private Geometry edgeOverlayGeometry;
    private Geometry highlightGeometry;

    private Material solidMaterial;
    private Material edgeOverlayMaterial;
    private Material faceHighlightMaterial;
    private Material edgeHighlightMaterial;

    private boolean wireframe;
    private boolean edgeOverlayVisible = true;
    private SelectMode selectMode = SelectMode.FACE;

    private ProtoMeshSnapshot currentSnapshot;
    private JmeMeshAdapter.TriangleMeshResult triangleResult;

    private FaceId selectedFace;
    private EdgeId selectedEdge;

    private BitmapText hud;
    private Vector2f mouseDownPosition;

    public static void main(String[] args) {
        GeometryEvaluatorApp app = new GeometryEvaluatorApp();
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Generative Mesh Library - Geometry Evaluator");
        settings.setWidth(1280);
        settings.setHeight(800);
        settings.setVSync(true);
        settings.setSamples(4);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        setDisplayStatView(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.06f, 0.08f, 1f));

        meshNode = new Node("mesh-root");
        rootNode.attachChild(meshNode);
        cameraTargetNode = new Node("camera-target");
        rootNode.attachChild(cameraTargetNode);

        addThreePointLighting();
        addGroundPlane();
        loadGeometry(new TubeGeometry());

        chaseCam = new ChaseCamera(cam, cameraTargetNode, inputManager);
        chaseCam.setDragToRotate(true);
        chaseCam.setDefaultVerticalRotation(FastMath.PI / 6f);
        chaseCam.setMinDistance(0.1f);
        chaseCam.setMaxDistance(500f);
        chaseCam.setZoomSensitivity(2f);

        setUpInput();
        setUpHud();
        frameAll();
    }

    // ---- Scene setup --------------------------------------------------

    private void addThreePointLighting() {
        DirectionalLight key = directionalLight(new Vector3f(-0.6f, -1f, -0.4f), ColorRGBA.White.mult(1.25f));
        DirectionalLight fillLight = directionalLight(new Vector3f(0.7f, -0.55f, -0.25f), new ColorRGBA(.55f, .65f, 1f, 1f));
        DirectionalLight rimLight = directionalLight(new Vector3f(0.25f, -0.7f, 0.8f), new ColorRGBA(1f, .72f, .48f, 1f));
        rootNode.addLight(key);
        rootNode.addLight(fillLight);
        rootNode.addLight(rimLight);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(.35f));
        rootNode.addLight(ambient);
    }

    private static DirectionalLight directionalLight(Vector3f direction, ColorRGBA color) {
        DirectionalLight light = new DirectionalLight();
        light.setDirection(direction.normalize());
        light.setColor(color);
        return light;
    }

    /** y = 0 ground plane with a red-clay PBR material. */
    private void addGroundPlane() {
        float halfSize = 20f;
        float thickness = 0.05f;
        Geometry plane = new Geometry("ground-plane", new Box(halfSize, thickness / 2f, halfSize));
        plane.setLocalTranslation(0, -thickness / 2f, 0);
        Material material = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        material.setColor("BaseColor", new ColorRGBA(0.55f, 0.20f, 0.12f, 1f));
        material.setFloat("Roughness", 0.85f);
        material.setFloat("Metallic", 0.0f);
        plane.setMaterial(material);
        plane.setShadowMode(RenderQueue.ShadowMode.Receive);
        rootNode.attachChild(plane);
    }

    /** Rebuilds the viewer's scene content from a {@link SUTGeometryInterface} generator. */
    public void loadGeometry(SUTGeometryInterface generator) {
        currentSnapshot = generator.generate();
        if (!currentSnapshot.isValid()) {
            throw new IllegalStateException("Generator '" + generator.id() + "' produced an invalid mesh: "
                    + currentSnapshot.issues());
        }
        selectedFace = null;
        selectedEdge = null;
        meshNode.detachAllChildren();

        TriangulatedMesh triangulated = ProtoMeshTriangulator.triangulate(currentSnapshot);
        triangleResult = JmeMeshAdapter.toTriangleMesh(triangulated);

        solidMaterial = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        solidMaterial.setColor("BaseColor", new ColorRGBA(0.62f, 0.62f, 0.65f, 1f));
        solidMaterial.setFloat("Roughness", 0.55f);
        solidMaterial.setFloat("Metallic", 0.05f);
        solidMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        solidMaterial.getAdditionalRenderState().setWireframe(wireframe);
        solidMaterial.getAdditionalRenderState().setPolyOffset(1f, 1f);

        solidGeometry = new Geometry("sut-solid", triangleResult.mesh());
        solidGeometry.setMaterial(solidMaterial);
        solidGeometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        meshNode.attachChild(solidGeometry);

        JmeMeshAdapter.EdgeMeshResult edgeMeshResult = JmeMeshAdapter.toEdgeMesh(currentSnapshot);
        edgeOverlayMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        edgeOverlayMaterial.setColor("Color", new ColorRGBA(0.03f, 0.03f, 0.03f, 1f));
        edgeOverlayGeometry = new Geometry("sut-edges", edgeMeshResult.mesh());
        edgeOverlayGeometry.setMaterial(edgeOverlayMaterial);
        edgeOverlayGeometry.setCullHint(edgeOverlayVisible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        meshNode.attachChild(edgeOverlayGeometry);

        faceHighlightMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        faceHighlightMaterial.setColor("Color", new ColorRGBA(1f, 0.55f, 0.05f, 0.65f));
        faceHighlightMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        faceHighlightMaterial.getAdditionalRenderState().setDepthTest(false);
        faceHighlightMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        edgeHighlightMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        edgeHighlightMaterial.setColor("Color", new ColorRGBA(0.15f, 0.95f, 1f, 1f));
        edgeHighlightMaterial.getAdditionalRenderState().setDepthTest(false);
        edgeHighlightMaterial.getAdditionalRenderState().setLineWidth(4f);

        updateHighlight();
    }

    // ---- Input ----------------------------------------------------------

    private void setUpInput() {
        addMapping("ToggleShaded", new KeyTrigger(com.jme3.input.KeyInput.KEY_1));
        addMapping("ToggleEdgeMesh", new KeyTrigger(com.jme3.input.KeyInput.KEY_2));
        addMapping("ToggleSelectMode", new KeyTrigger(com.jme3.input.KeyInput.KEY_TAB));
        addMapping("FrameSelection", new KeyTrigger(com.jme3.input.KeyInput.KEY_F));
        addMapping("FrameAll", new KeyTrigger(com.jme3.input.KeyInput.KEY_HOME));
        addMapping("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
    }

    private void addMapping(String name, com.jme3.input.controls.Trigger trigger) {
        inputManager.addMapping(name, trigger);
        inputManager.addListener(this, name);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "ToggleShaded" -> { if (isPressed) toggleWireframe(); }
            case "ToggleEdgeMesh" -> { if (isPressed) toggleEdgeOverlay(); }
            case "ToggleSelectMode" -> { if (isPressed) toggleSelectMode(); }
            case "FrameSelection" -> { if (isPressed) frameSelectionOrAll(); }
            case "FrameAll" -> { if (isPressed) frameAll(); }
            case "Select" -> onSelectButton(isPressed);
            default -> { }
        }
    }

    private void onSelectButton(boolean isPressed) {
        InputManager input = inputManager;
        if (isPressed) {
            mouseDownPosition = input.getCursorPosition().clone();
        } else if (mouseDownPosition != null) {
            Vector2f released = input.getCursorPosition();
            if (mouseDownPosition.distance(released) <= CLICK_DRAG_THRESHOLD_PIXELS) {
                pickAtCursor(released);
            }
            mouseDownPosition = null;
        }
    }

    // ---- Toggles ----------------------------------------------------------

    private void toggleWireframe() {
        wireframe = !wireframe;
        solidMaterial.getAdditionalRenderState().setWireframe(wireframe);
        updateHud();
    }

    private void toggleEdgeOverlay() {
        edgeOverlayVisible = !edgeOverlayVisible;
        edgeOverlayGeometry.setCullHint(edgeOverlayVisible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        updateHud();
    }

    private void toggleSelectMode() {
        selectMode = selectMode == SelectMode.FACE ? SelectMode.EDGE : SelectMode.FACE;
        updateHud();
    }

    // ---- Picking / selection -----------------------------------------------

    private void pickAtCursor(Vector2f screenPosition) {
        Vector3f origin = cam.getWorldCoordinates(screenPosition, 0f);
        Vector3f target = cam.getWorldCoordinates(screenPosition, 1f);
        Ray ray = new Ray(origin, target.subtractLocal(origin).normalizeLocal());

        CollisionResults results = new CollisionResults();
        solidGeometry.collideWith(ray, results);
        if (results.size() == 0) {
            selectedFace = null;
            selectedEdge = null;
            updateHighlight();
            updateHud();
            return;
        }
        CollisionResult closest = results.getClosestCollision();
        FaceId faceId = triangleResult.faceOf(closest.getTriangleIndex());
        if (selectMode == SelectMode.FACE) {
            selectedFace = faceId;
            selectedEdge = null;
        } else {
            selectedFace = null;
            selectedEdge = nearestEdgeOfFace(faceId, closest.getContactPoint());
        }
        updateHighlight();
        updateHud();
    }

    /** Finds the boundary edge of {@code faceId} closest to a picked point on that face. */
    private EdgeId nearestEdgeOfFace(FaceId faceId, Vector3f point) {
        ProtoFace face = currentSnapshot.faces().get(faceId);
        EdgeId best = null;
        float bestDistance = Float.MAX_VALUE;
        List<LoopId> loops = face.loops();
        for (int index = 0; index < loops.size(); index++) {
            ProtoLoop loop = currentSnapshot.loops().get(loops.get(index));
            ProtoLoop nextLoop = currentSnapshot.loops().get(loops.get((index + 1) % loops.size()));
            Vector3f a = JmeMeshAdapter.toVector3f(currentSnapshot.vertices().get(loop.vertexId()).position());
            Vector3f b = JmeMeshAdapter.toVector3f(currentSnapshot.vertices().get(nextLoop.vertexId()).position());
            float distance = distancePointToSegment(point, a, b);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = loop.edgeId();
            }
        }
        return best;
    }

    private static float distancePointToSegment(Vector3f point, Vector3f a, Vector3f b) {
        Vector3f ab = b.subtract(a);
        float lengthSquared = ab.lengthSquared();
        float t = lengthSquared <= 1.0e-12f ? 0f : (point.subtract(a).dot(ab)) / lengthSquared;
        t = FastMath.clamp(t, 0f, 1f);
        Vector3f closest = a.add(ab.mult(t));
        return point.distance(closest);
    }

    /** Rebuilds the highlight overlay geometry for the current selection, if any. */
    private void updateHighlight() {
        if (highlightGeometry != null) {
            highlightGeometry.removeFromParent();
            highlightGeometry = null;
        }
        if (selectedFace != null) {
            highlightGeometry = new Geometry("selection-highlight-face", buildFaceHighlightMesh(selectedFace));
            highlightGeometry.setMaterial(faceHighlightMaterial);
            meshNode.attachChild(highlightGeometry);
        } else if (selectedEdge != null) {
            Vector3f[] endpoints = JmeMeshAdapter.edgeEndpoints(currentSnapshot, selectedEdge);
            highlightGeometry = new Geometry("selection-highlight-edge", buildEdgeHighlightMesh(endpoints));
            highlightGeometry.setMaterial(edgeHighlightMaterial);
            meshNode.attachChild(highlightGeometry);
        }
    }

    /** Fan-triangulates a (convex) face's corners for the highlight overlay. */
    private Mesh buildFaceHighlightMesh(FaceId faceId) {
        ProtoFace face = currentSnapshot.faces().get(faceId);
        List<Vector3f> corners = new ArrayList<>(face.loops().size());
        for (LoopId loopId : face.loops()) {
            ProtoLoop loop = currentSnapshot.loops().get(loopId);
            ProtoVertex vertex = currentSnapshot.vertices().get(loop.vertexId());
            corners.add(JmeMeshAdapter.toVector3f(vertex.position()));
        }
        List<Integer> indices = new ArrayList<>();
        for (int index = 1; index < corners.size() - 1; index++) {
            indices.add(0);
            indices.add(index);
            indices.add(index + 1);
        }
        java.nio.FloatBuffer positions = BufferUtils.createFloatBuffer(corners.size() * 3);
        corners.forEach(corner -> positions.put(corner.x).put(corner.y).put(corner.z));
        int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indexArray);
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
    }

    private Mesh buildEdgeHighlightMesh(Vector3f[] endpoints) {
        java.nio.FloatBuffer positions = BufferUtils.createFloatBuffer(6);
        positions.put(endpoints[0].x).put(endpoints[0].y).put(endpoints[0].z);
        positions.put(endpoints[1].x).put(endpoints[1].y).put(endpoints[1].z);
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 2, new int[] {0, 1});
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
    }

    // ---- Framing ----------------------------------------------------------

    private void frameSelectionOrAll() {
        List<Vector3f> points = selectionPoints();
        if (points.isEmpty()) {
            frameAll();
        } else {
            frame(points);
        }
    }

    private void frameAll() {
        List<Vector3f> points = new ArrayList<>(currentSnapshot.vertices().size());
        for (ProtoVertex vertex : currentSnapshot.vertices().values()) {
            points.add(JmeMeshAdapter.toVector3f(vertex.position()));
        }
        frame(points);
    }

    private List<Vector3f> selectionPoints() {
        List<Vector3f> points = new ArrayList<>();
        if (selectedFace != null) {
            ProtoFace face = currentSnapshot.faces().get(selectedFace);
            for (LoopId loopId : face.loops()) {
                ProtoLoop loop = currentSnapshot.loops().get(loopId);
                points.add(JmeMeshAdapter.toVector3f(currentSnapshot.vertices().get(loop.vertexId()).position()));
            }
        } else if (selectedEdge != null) {
            ProtoEdge edge = currentSnapshot.edges().get(selectedEdge);
            points.add(JmeMeshAdapter.toVector3f(currentSnapshot.vertices().get(edge.vertexA()).position()));
            points.add(JmeMeshAdapter.toVector3f(currentSnapshot.vertices().get(edge.vertexB()).position()));
        }
        return points;
    }

    private void frame(List<Vector3f> points) {
        if (points.isEmpty()) return;
        Vector3f min = points.get(0).clone();
        Vector3f max = points.get(0).clone();
        for (Vector3f point : points) {
            min.minLocal(point);
            max.maxLocal(point);
        }
        Vector3f center = min.add(max).multLocal(0.5f);
        float radius = Math.max(0.25f, center.distance(max));

        cameraTargetNode.setLocalTranslation(center);
        chaseCam.setDefaultDistance(radius * 3f);
        chaseCam.setMinDistance(Math.max(0.05f, radius * 0.15f));
        chaseCam.setMaxDistance(Math.max(200f, radius * 20f));
        // ChaseCamera reads its distance lazily from the default the next update;
        // nudge it immediately so framing feels instantaneous.
        cam.setLocation(center.add(0, radius * 0.6f, radius * 3f));
        cam.lookAt(center, Vector3f.UNIT_Y);
    }

    // ---- HUD ----------------------------------------------------------

    private void setUpHud() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(font);
        hud.setSize(font.getCharSet().getRenderedSize());
        hud.setLocalTranslation(8, cam.getHeight() - 8, 0);
        guiNode.attachChild(hud);
        updateHud();
    }

    private void updateHud() {
        if (hud == null) return;
        hud.setText(String.join("\n", List.of(
                "Generative Mesh Library - Geometry Evaluator",
                "Drag mouse (L/R) to orbit, scroll to zoom",
                "[1] Shaded/Wireframe: " + (wireframe ? "Wireframe" : "Shaded"),
                "[2] Edge mesh overlay: " + (edgeOverlayVisible ? "On" : "Off"),
                "[Tab] Select mode: " + selectMode,
                "[Click] Select " + (selectMode == SelectMode.FACE ? "face" : "edge"),
                "[F] Frame selection  [Home] Frame all",
                "Selected face: " + (selectedFace == null ? "-" : selectedFace),
                "Selected edge: " + (selectedEdge == null ? "-" : selectedEdge))));
    }
}
