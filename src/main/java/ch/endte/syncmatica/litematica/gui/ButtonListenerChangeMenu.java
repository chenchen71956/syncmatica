package ch.endte.syncmatica.litematica.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import net.minecraft.client.gui.screen.Screen;

public class ButtonListenerChangeMenu implements IButtonActionListener {

    private final MainMenuButtonType type;
    private final Screen parent;

    public ButtonListenerChangeMenu(final MainMenuButtonType type, final Screen parent) {
        this.type = type;
        this.parent = parent;
    }

    @Override
    public void actionPerformedWithButton(final ButtonBase arg0, final int arg1) {
        GuiBase gui = null;
        switch (type) {
            case VIEW_SYNCMATICS:
                gui = new GuiSyncmaticaServerPlacementList();
                break;
            default:
                break;
        }
        if (gui != null) {
            gui.setParent(parent);
            GuiBase.openGui(gui);
        }
    }

}