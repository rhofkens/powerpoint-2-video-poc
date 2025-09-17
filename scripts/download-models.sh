#!/bin/bash

# Download Diffusion Model
echo "========================================="
echo "Downloading Diffusion Model..."
echo "========================================="
wget --show-progress -q -O "diffusion_models/wan2.1_i2v_480p_14B_fp16.safetensors" \
  "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_i2v_480p_14B_fp16.safetensors"

# Download Lightxv LoRA
echo "========================================="
echo "Downloading Lightxv LoRA..."
echo "========================================="
wget --show-progress -q -O "loras/Wan21_T2V_14B_lightx2v_cfg_step_distill_lora_rank32.safetensors" \
  "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/Wan21_T2V_14B_lightx2v_cfg_step_distill_lora_rank32.safetensors"

# Download Infinite Talk
echo "========================================="
echo "Downloading Infinite Talk..."
echo "========================================="
wget --show-progress -q -O "diffusion_models/Wan2_1-InfiniTetalk-Single_fp16.safetensors" \
  "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/InfiniteTalk/Wan2_1-InfiniTetalk-Single_fp16.safetensors"

# Download Clip Vision
echo "========================================="
echo "Downloading Clip Vision..."
echo "========================================="
wget --show-progress -q -O "clip_vision/clip_vision_h.safetensors" \
  "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/clip_vision/clip_vision_h.safetensors"

# Download Text Encoder
echo "========================================="
echo "Downloading Text Encoder..."
echo "========================================="
wget --show-progress -q -O "text_encoders/umt5_xxl_fp16.safetensors" \
  "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp16.safetensors"

# Download VAE
echo "========================================="
echo "Downloading VAE..."
echo "========================================="
wget --show-progress -q -O "vae/wan_2.1_vae.safetensors" \
  "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/vae/wan_2.1_vae.safetensors"

echo "========================================="
echo "All models downloaded successfully!"
echo "========================================="