# Wigglefish Image Workflows

The local ComfyUI install and model weights live under `tools/comfyui/` and are excluded from Git. Reusable workflow JSON files live in this folder.

## Start ComfyUI

From the workspace root, run:

```powershell
.\tools\start_image_generator.ps1
```

Then open `http://127.0.0.1:8188`. The server binds to loopback only.

## Blue-Ringed Octopus Workflows

- `wigglefish_blue_ring_octopus_api.json`: naturalistic photo/concept exploration.
- `wigglefish_blue_ring_octopus_refine_api.json`: low-denoise color refinement while preserving a selected pose.
- `wigglefish_blue_ring_octopus_color_api.json`: stronger cobalt-ring color pass.
- `wigglefish_blue_ring_octopus_3d_cartoon_api.json`: stylized 3D animated-character render.
- `wigglefish_blue_ring_octopus_fullbody_3d_api.json`: full-body composition with all arms visible.
- `wigglefish_blue_ring_octopus_pose_loop_api.json`: low-denoise arm-pose animation-frame experiment.

The local checkpoint is RealVisXL V4, an SDXL-family model distributed under OpenRAIL++. ComfyUI writes renders to `tools/comfyui/output/`; review candidates before promoting one into Android resources.

The current Android pet animation uses four Copilot Image Creator pose images as fixed-position mood-driven keyframes. The ComfyUI workflows are for future artwork generation and refinement; they are not required to run the app.
