# syntax=docker/dockerfile:1
# ==============================================================================
# TontOS AOSP ROM Alpine Builder
# Minimalist Alpine-based container environment for compiling TontOS AOSP platform.
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================

FROM alpine:3.20

LABEL maintainer="FalseThinks <paproken@protonmail.com>"
LABEL description="Minimalist Alpine Linux builder for TontOS AOSP ROM"

# 1. Install base utilities, build toolchains, and JDK
RUN apk update && apk add --no-cache \
    bash \
    coreutils \
    findutils \
    gawk \
    grep \
    sed \
    diffutils \
    patch \
    git \
    git-lfs \
    curl \
    wget \
    tar \
    gzip \
    bzip2 \
    xz \
    zip \
    unzip \
    rsync \
    file \
    which \
    procps \
    ncurses \
    ncurses-dev \
    openssl \
    openssl-dev \
    zlib \
    zlib-dev \
    bc \
    bison \
    flex \
    m4 \
    make \
    gcc \
    g++ \
    musl-dev \
    linux-headers \
    python3 \
    py3-pip \
    python3-dev \
    openjdk17 \
    ccache \
    sudo \
    shadow \
    jq \
    # Glibc compatibility layer for AOSP prebuilt host binaries
    gcompat \
    libc6-compat

# 2. Configure glibc dynamic linker compatibility symlinks
# AOSP prebuilts expect /lib64/ld-linux-x86-64.so.2 and standard glibc lib paths
RUN mkdir -p /lib64 /usr/lib64 && \
    if [ -f /lib/ld-linux-x86-64.so.2 ]; then \
        ln -sf /lib/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2; \
    elif [ -f /lib/ld-musl-x86_64.so.1 ]; then \
        ln -sf /lib/ld-musl-x86_64.so.1 /lib64/ld-linux-x86-64.so.2; \
    fi

# 3. Install Google repo tool
RUN curl -s https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+rx /usr/local/bin/repo

# 4. Create non-root build user matching host UID/GID (1000:1000)
# This prevents permission mismatch when mounting host AOSP source trees
RUN groupadd -g 1000 tontos && \
    useradd -u 1000 -g tontos -m -s /bin/bash tontos && \
    echo "tontos ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# 5. Environment configuration for AOSP & Soong
ENV USER=tontos
ENV HOME=/home/tontos
ENV LANG=C.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV PATH=/usr/lib/ccache/bin:/usr/local/bin:/usr/bin:/bin:$PATH
ENV USE_CCACHE=1
ENV CCACHE_DIR=/home/tontos/.ccache
ENV CCACHE_EXEC=/usr/bin/ccache
ENV CCACHE_MAXSIZE=50G
ENV CCACHE_SLOPPINESS=time_macros,include_file_mtime,file_macro
ENV NINJA_STATUS="[%p %f/%t %e] "

USER tontos
WORKDIR /home/tontos/aosp

# 6. Default command launches bash with AOSP environment prompt
CMD ["/bin/bash"]
