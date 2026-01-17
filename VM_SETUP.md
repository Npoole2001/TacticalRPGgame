# Desktop VM Setup Guide

HexControl is a desktop OpenGL application (LWJGL/GLFW). In virtualized
environments you must enable 3D acceleration and install the VM guest tools so
OpenGL 3.3+ is available.

## 1) VM Settings (All Hosts)

- **Enable 3D acceleration** in your VM display settings.
- **Allocate enough video memory** (128 MB+ recommended).
- **Install guest tools** so the virtual GPU driver is active.

## 2) Linux Guest (Ubuntu/Debian)

Install common GLFW/OpenGL dependencies:

```bash
sudo apt update
sudo apt install -y \
  libgl1 libglx-mesa0 libglu1-mesa \
  libxrandr2 libxinerama1 libxcursor1 libxi6 libx11-6 libxext6 \
  mesa-utils
```

Verify OpenGL version:

```bash
glxinfo | rg "OpenGL version"
```

If you cannot enable 3D acceleration, try Mesa software rendering:

```bash
LIBGL_ALWAYS_SOFTWARE=1 ./gradlew run
```

## 3) Windows Guest

- Install **Guest Additions** (VirtualBox) or **VMware Tools**.
- Reboot after install.
- Confirm OpenGL 3.3+ in a tool like OpenGL Extensions Viewer.

Run:

```powershell
.\gradlew.bat run
```

## 4) macOS Guest

macOS guests are best supported on Apple hardware. Ensure the VM supports Metal
or OpenGL 3.3+. If you are running on Apple Silicon, select a VM product that
passes through the GPU (e.g., VMware Fusion Tech Preview/Parallels).

Run:

```bash
./gradlew run
```

## 5) Common Troubleshooting

**Window does not appear / crash on launch**
- Check that OpenGL 3.3+ is available.
- Confirm 3D acceleration and guest tools are installed.

**GLFW errors about missing libraries (Linux)**
- Reinstall the packages listed above.

**macOS issues**
- LWJGL on macOS requires:
  - `-XstartOnFirstThread` (already set in `build.gradle`)

If the VM cannot provide OpenGL 3.3+ at all, run on a physical machine or enable
GPU passthrough.
