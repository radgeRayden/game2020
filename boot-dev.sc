using import radlib.core-extensions
switch operating-system
case 'linux
    load-library "libX11.so"
case 'windows
    ;
default
    ;

run-stage;
using import glm
import .raydEngine.use
import HID

let gfx = (import gfx-wgpu)
let wgpu = (import foreign.wgpu-native)

HID.init (HID.WindowOptions (visible? = false)) (HID.GfxAPI.WebGPU)

HID.on-window-resized =
    gfx.update-backbuffer-size
HID.on-key-event =
    fn "key-callback" (ev)
        using HID.keyboard
        if (keybind ev KeyModifier.ALT KeyCode.ENTER)
            HID.window.toggle-fullscreen;

        if (keybind ev KeyCode.ESCAPE)
            HID.window.close;

let width height = (HID.window.size)
gfx.init (HID.window.create-wgpu-surface) width height

HID.window.toggle-visible;

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    local cmd-encoder = (gfx.CommandEncoder)

    let render-pass =
        try
            local rp = (gfx.screen-pass cmd-encoder (vec4 0.017 0.017 0.017 1.0))
        except (ex)
            # will error if backbuffer couldn't be acquired this frame.
            continue;

    'finish render-pass
    local cmdbuf = ('finish cmd-encoder)
    'submit cmdbuf
    gfx.present;
;

let b = gfx.backend
wgpu.device_destroy b.device
wgpu.adapter_destroy b.adapter
