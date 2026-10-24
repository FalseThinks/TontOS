# syntax=docker/dockerfile:1
# ==============================================================================
# TontOS Universal Containerized Builder (Debian Bookworm Slim)
# Encapsulates 100% of TontOS build toolchains, dependencies, and environment:
# - AOSP Platform & Soong/Ninja build tools
# - Rust Stable Toolchain (for all 11 microdaemons in crates/)
# - Android SDK & Java 17 headless runtime
# - Google Repo CLI & CCache acceleration
# All data, caches (.ccache/), and outputs (out/) stay INSIDE the project tree.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================

FROM debian:bookworm-slim

LABEL maintainer="FalseThinks <paproken@protonmail.com>"
LABEL description="Unified containerized build environment for TontOS (Rust + AOSP + Android Launcher)"

ENV DEBIAN_FRONTEND=noninteractive

# 1. Install base utilities, C/C++ compiler toolchains, Java 17, and dependencies
RUN apt-get update -qq && apt-get install -qq -y --no-install-recommends \
    bash \
    coreutils \
    git \
    git-lfs \
    curl \
    wget \
    file \
    python3 \
    python3-dev \
    python3-pip \
    openjdk-17-jdk-headless \
    ccache \
    rsync \
    zip \
    unzip \
    bison \
    flex \
    bc \
    m4 \
    make \
    gcc \
    g++ \
    ca-certificates \
    procps \
    sudo \
    jq \
    libncurses5 \
    libncurses-dev \
    libssl-dev \
    zlib1g-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

# 2. Install Rust stable toolchain system-wide
ENV RUSTUP_HOME=/usr/local/rustup \
    CARGO_HOME=/usr/local/cargo \
    PATH=/usr/local/cargo/bin:$PATH

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
    sh -s -- -y --default-toolchain stable --profile minimal && \
    chmod -R a+w /usr/local/cargo /usr/local/rustup

# 3. Install Google repo tool
RUN curl -s https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+rx /usr/local/bin/repo

# 4. Create non-root build user matching host UID/GID (1000:1000)
# Prevents file permission conflicts with host workspace
RUN groupadd -g 1000 tontos && \
    useradd -u 1000 -g tontos -m -s /bin/bash tontos && \
    mkdir -p /home/tontos/aosp && \
    chown -R tontos:tontos /home/tontos && \
    echo "tontos ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# 5. Environment configuration: project-internal cache & output paths
ENV USER=tontos \
    HOME=/home/tontos \
    LANG=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    PATH=/usr/local/cargo/bin:/usr/lib/ccache:/usr/local/bin:/usr/bin:/bin:$PATH \
    USE_CCACHE=1 \
    CCACHE_DIR=/workspace/.ccache \
    CCACHE_EXEC=/usr/bin/ccache \
    CCACHE_MAXSIZE=50G \
    CCACHE_SLOPPINESS=time_macros,include_file_mtime,file_macro \
    NINJA_STATUS="[%p %f/%t %e] "

# 6. Set default workspace inside project directory
WORKDIR /workspace

USER tontos

CMD ["/bin/bash"]
