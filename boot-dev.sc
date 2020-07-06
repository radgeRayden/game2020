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
using import glsl
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

vvv bind vertex
gfx.Shader 'vertex
    fn ()
        using import glm
        using import glsl
        local vertices =
            arrayof vec4
                vec4 -0.5 -0.5 0.0 1.0
                vec4  0.5 -0.5 0.0 1.0
                vec4  0.0  0.5 0.0 1.0

        gl_Position = (vertices @ gl_VertexIndex)

vvv bind fragment
gfx.Shader 'fragment
    fn ()
        using import glm
        using import glsl
        out color : vec4
            location = 0
        let x y u v = (unpack (gl_FragCoord / (vec4 1280 720 1 1)))
        color = (vec4 x y u 1)

let render-pip =
    gfx.RenderPipeline vertex fragment

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;
    local cmd-encoder = (gfx.CommandEncoder)

    let render-pass =
        try
            local rp = (gfx.screen-pass cmd-encoder (vec4 0.017 0.017 0.017 1.0))
        except (ex)
            # will error if backbuffer couldn't be acquired this frame.
            continue;

    'set-pipeline render-pass render-pip
    'draw render-pass 3 1 0 0
    'finish render-pass render-pip
    local cmdbuf = ('finish cmd-encoder)
    'submit cmdbuf
    gfx.present;
;
