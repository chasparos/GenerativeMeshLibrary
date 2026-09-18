package com.planeguardian.assets.eval.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the shared on-screen HUD {@link BitmapText} and rebuilds it every frame from a list of
 * {@link HudProvider}s, so each {@code AppState} (or "mode") contributes only its own lines
 * instead of one monolithic HUD string being assembled in the main application.
 *
 * <p>Providers are rendered in registration order; each may return zero or more lines. A provider
 * that is momentarily inactive can simply return an empty list to drop out of the HUD.</p>
 */
public final class ViewerHudAppState extends BaseAppState {

    /** A contributor of HUD text lines (typically another {@code AppState}). */
    public interface HudProvider {
        /** The lines this provider currently wants shown, top to bottom; empty to contribute nothing. */
        List<String> hudLines();
    }

    private final List<HudProvider> providers = new CopyOnWriteArrayList<>();
    private BitmapText hud;
    private Node guiNode;

    /** Registers a provider whose lines are appended to the HUD each frame. */
    public void addProvider(HudProvider provider) {
        providers.add(provider);
    }

    public void removeProvider(HudProvider provider) {
        providers.remove(provider);
    }

    @Override
    protected void initialize(Application app) {
        SimpleApplication simpleApp = (SimpleApplication) app;
        this.guiNode = simpleApp.getGuiNode();
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(font);
        hud.setSize(font.getCharSet().getRenderedSize());
        hud.setColor(ColorRGBA.White);
        hud.setLocalTranslation(8, app.getCamera().getHeight() - 8, 0);
        guiNode.attachChild(hud);
    }

    @Override
    protected void cleanup(Application app) {
        if (hud != null) {
            hud.removeFromParent();
            hud = null;
        }
    }

    @Override
    protected void onEnable() {
        if (hud != null) hud.setCullHint(Spatial.CullHint.Inherit);
    }

    @Override
    protected void onDisable() {
        if (hud != null) hud.setCullHint(Spatial.CullHint.Always);
    }

    @Override
    public void update(float tpf) {
        if (hud == null) return;
        List<String> lines = new ArrayList<>();
        for (HudProvider provider : providers) {
            lines.addAll(provider.hudLines());
        }
        hud.setText(String.join("\n", lines));
    }
}
