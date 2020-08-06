import .raydEngine.use
using import radlib.core-extensions
let wgpu = (import foreign.wgpu-native)


do
    define-scope binding-layouts
        let 2d-sampled-texture =
            wgpu.BindGroupLayoutEntry
                binding = 0
                visibility = wgpu.ShaderStage_FRAGMENT
                ty = wgpu.BindingType.SampledTexture
                view_dimension = wgpu.TextureViewDimension.D2
                texture_component_type = wgpu.TextureComponentType.Uint

        let sampler =
            wgpu.BindGroupLayoutEntry
                binding = 0
                visibility = wgpu.ShaderStage_FRAGMENT
                ty = wgpu.BindingType.Sampler
                multisampled = false


    define-scope bindings
        fn make-buffer-descriptor (binding buffer offset size)
            wgpu.BindGroupEntry
                resource =
                    wgpu.BindingResource
                        tag = wgpu.BindingResource_Tag.Buffer
                        typeinit
                            buffer =
                                typeinit
                                    wgpu.BufferBinding
                                        buffer = buffer
                                        offset = offset
                                        size = size

        fn make-texture-view-descriptor (binding texture-view)
            binding as:= u32
            wgpu.BindGroupEntry
                resource =
                    wgpu.BindingResource
                        tag = wgpu.BindingResource_Tag.TextureView
                        typeinit
                            texture_view =
                                typeinit
                                    texture-view

        fn make-sampler-descriptor (binding sampler-id)
            binding as:= u32
            wgpu.BindGroupEntry
                binding = binding
                resource =
                    wgpu.BindingResource
                        tag = wgpu.BindingResource_Tag.Sampler
                        typeinit
                            sampler =
                                typeinit
                                    sampler-id

    define-scope objects
        let 2d-texture-view =
            wgpu.TextureViewDescriptor
                format = wgpu.TextureFormat.Rgba8Unorm
                dimension = wgpu.TextureViewDimension.D2
                aspect = wgpu.TextureAspect.All
                base_mip_level = 0
                level_count = 1
                base_array_layer = 0
                array_layer_count = 1

        let sprite-sampler =
            wgpu.SamplerDescriptor
                address_mode_u = wgpu.AddressMode.ClampToEdge
                address_mode_v = wgpu.AddressMode.ClampToEdge
                address_mode_w = wgpu.AddressMode.ClampToEdge
                mag_filter = wgpu.FilterMode.Nearest
                min_filter = wgpu.FilterMode.Nearest
                mipmap_filter = wgpu.FilterMode.Nearest
                lod_min_clamp = -100.0
                lod_max_clamp = 100.0
                compare = wgpu.CompareFunction.Always

    locals;
