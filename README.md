# GenerativeMeshLibrary

Engine-agnostic mesh editing library extracted from `chasparos/PlaneGuardianAssets`.

## Included packages

- `com.planeguardian.assets.generation.topology`
- `com.planeguardian.assets.generation.geometry.operations`
- `com.planeguardian.assets.generation.geometry.eval` — `SUTGeometryInterface` and a basic `TubeGeometry` implementation used to exercise the geometry tools
- `com.planeguardian.assets.generation.triangulation`
- `com.planeguardian.assets.generation.adapters.jme` — converts engine-agnostic meshes to jME3 `Mesh` objects (triangle mesh + edge-line mesh) for the evaluation viewer
- `com.planeguardian.assets.eval` — `GeometryEvaluatorApp`, a basic JME3 desktop app for visually exercising the geometry tools

Supporting math/API primitives required by these packages were copied as well.

## Build

```bash
mvn test
mvn package
```

## Geometry evaluation viewer

`GeometryEvaluatorApp` is a small JME3 (LWJGL3) desktop app — the same JME3 stack used by
`chasparos/PlaneGuardianAssets` (`jme3-core`, `jme3-desktop`, `jme3-lwjgl3`) — for visually
exercising `SUTGeometryInterface` implementations such as `TubeGeometry`. Run it with:

```bash
mvn -q exec:java
```

Controls:

- **Orbit camera**: drag with left or right mouse button to rotate, scroll to zoom.
- **`1`**: toggle shaded / wireframe.
- **`2`**: toggle the "edge mesh" overlay (renders every topology edge).
- **`Tab`**: toggle between face selection and edge selection modes.
- **Click** (without dragging): select the face or edge under the cursor.
- **`F`**: frame the current selection (or the whole geometry if nothing is selected).
- **`Home`**: frame the whole geometry.

The scene includes three-point directional lighting plus a red-clay PBR material ground
plane at `y = 0`.
