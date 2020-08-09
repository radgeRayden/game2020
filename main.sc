using import radlib.core-extensions
import .raydEngine.use
import HID
import .renderer

# Platform code initialization
HID.init (HID.WindowOptions (visible? = true)) (HID.GfxAPI.WebGPU)

# module initialization
renderer.init;

# basic keybindings for use during testing, will be abstracted later when I have a proper
# input handling module.
HID.on-key-event =
    fn "key-callback" (ev)
        using HID.keyboard
        if (keybind ev KeyModifier.ALT KeyCode.ENTER)
            HID.window.toggle-fullscreen;

        if (keybind ev KeyCode.ESCAPE)
            HID.window.close;

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    renderer.frame;
