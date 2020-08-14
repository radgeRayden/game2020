let AppSettings = (import radlib.app-settings)
using import struct
using import enum
using import Array
using import property

import .raydEngine.use
let stbi = (import foreign.stbi)
import .filesystem

enum ImageFormat plain
    RGBA8

fn... load-image (data : (Array u8), format)
    local channel-count : i32
    local width : i32
    local height : i32

    # NOTE: change this when supporting other image formats
    let desired-channels = 4

    let imgbytes =
        stbi.load_from_memory \
            data ((countof data) as i32) &width &height &channel-count desired-channels

    if (imgbytes == null)
        using import radlib.libc
        stdio.printf "%s %s" ("cannot load image:" as rawstring) (stbi.failure_reason)
        raise false

    # TODO: replace with Struct.__typecall
    let byte-array = (ptrtoref (alloca (Array u8)))
    byte-array._items = imgbytes
    let size =
        switch format
        case ImageFormat.RGBA8
            (width * height * channel-count)
        default
            raise false
    byte-array._count = size
    byte-array._capacity = byte-array._count

    _ byte-array width height channel-count

struct ImageData
    data : (Array u8)
    width : u32
    height : u32
    channel-count : i32
    format : ImageFormat

    vvv bind Size
    property
        inline "get" (self)
            countof self.data
           
    inline __typecall (cls data)
        let bytes =
            static-match (typeof data)
            case filesystem.FileData
                data.data
            case ((Array u8) or (Array i8))
                data
            default
                static-error "data is of unsupported type"

        let data width height channels = (load-image bytes ImageFormat.RGBA8)
        super-type.__typecall cls
            data = data
            width = (width as u32)
            height = (height as u32)
            channel-count = channels
            format = ImageFormat.RGBA8

do
    let ImageFormat ImageData
    locals;
