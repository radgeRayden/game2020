using import glm

import .raydEngine.use
let gfx = (import gfx-wgpu)
let glfw = (import foreign.glfw)

let AppSettings = (import radlib.app-settings)
'set-symbol AppSettings 'ReleaseMode? true
load-library (module-dir .. "/dist/lib/libraydengine.so")
run-stage;

let get-window-handle =
    extern 'get-window-handle (function (mutable pointer glfw.window))
let signal-loading-finished =
    extern 'signal-loading-finished (function void)
let get-gfx-state =
    extern 'get-gfx-state (function gfx.GfxState)

signal-loading-finished;
let app-window = (get-window-handle)

import HID
let mwidth mheight = (HID.window.monitor-size)
HID.init
    HID.WindowOptions
        title = "my amazing game"
        x = ((mwidth // 2) - (1280 // 2))
        y = ((mheight // 2) - (720 // 2))
        width = 1280
        height = 720
    app-window

gfx.backend = (get-gfx-state)
gfx.update-backbuffer-size 1280 720

while (not (glfw.WindowShouldClose app-window))
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
