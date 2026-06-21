let scene, camera, renderer, controls;
let waterParticles = [];
let drivingSprocket, drivenSprocket;
let animationSpeed = 1.0;
let showChain = true, showWater = true, showGrid = true;
let time = 0;
let waterTrough;
let chainLinkMesh, pinMesh, scraperMesh;
let chainLinkDummy, scraperDummy;
let _chainPathCache = [];

const CONFIG = {
    chainLength: 15.5,
    numLinks: 120,
    sprocketRadius: 0.35,
    drivenRadius: 0.33,
    centerDistance: 7.0,
    sprocketTeeth: 20,
    scraperCount: 24,
    scraperWidth: 0.3,
    scraperHeight: 0.15,
    scraperDepth: 0.12
};

const ChainPump3D = {
    init: function() {
        const canvas = document.getElementById('three-canvas');

        scene = new THREE.Scene();
        scene.background = new THREE.Color(0x0a0a1a);
        scene.fog = new THREE.Fog(0x0a0a1a, 15, 35);

        camera = new THREE.PerspectiveCamera(50, canvas.clientWidth / canvas.clientHeight, 0.1, 1000);
        camera.position.set(8, 6, 10);
        camera.lookAt(3.5, 0.5, 0);

        renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
        renderer.setSize(canvas.clientWidth, canvas.clientHeight);
        renderer.setPixelRatio(window.devicePixelRatio);
        renderer.shadowMap.enabled = true;
        renderer.shadowMap.type = THREE.PCFSoftShadowMap;

        setupLights();
        setupGroundAndGrid();
        createWaterwheelFrame();
        createSprockets();
        createChain();
        createScrapers();
        createWaterTrough();
        createWaterParticles();

        setupControls();
        setupCanvasEvents();

        animate();
        window.addEventListener('resize', onWindowResize);

        ChainPump3D.scene = scene;
        ChainPump3D.camera = camera;
        ChainPump3D.renderer = renderer;
        ChainPump3D.chainLinkMesh = chainLinkMesh;
        ChainPump3D.pinMesh = pinMesh;
        ChainPump3D.scraperMesh = scraperMesh;
        ChainPump3D.waterParticles = waterParticles;
        ChainPump3D.drivingSprocket = drivingSprocket;
        ChainPump3D.drivenSprocket = drivenSprocket;
    },

    updateChainLinkPositions: function(positions) {
        const tmpScale = new THREE.Vector3(1, 1, 1);
        Object.keys(positions).forEach((id, idx) => {
            if (idx < CONFIG.numLinks && positions[id] && chainLinkMesh) {
                const [x, y, z] = positions[id];
                const tmpMatrix = new THREE.Matrix4();
                chainLinkMesh.getMatrixAt(idx, tmpMatrix);
                const tmpPos = new THREE.Vector3();
                const tmpQ = new THREE.Quaternion();
                tmpMatrix.decompose(tmpPos, tmpQ, tmpScale);

                chainLinkDummy.position.set(x, y + CONFIG.sprocketRadius, z || 0);
                chainLinkDummy.quaternion.copy(tmpQ);
                chainLinkDummy.updateMatrix();
                chainLinkMesh.setMatrixAt(idx, chainLinkDummy.matrix);
            }
        });
        chainLinkMesh.instanceMatrix.needsUpdate = true;
    },

    getConfig: function() {
        return CONFIG;
    }
};

function setupLights() {
    const ambient = new THREE.AmbientLight(0x404060, 0.5);
    scene.add(ambient);

    const mainLight = new THREE.DirectionalLight(0xffffff, 1.0);
    mainLight.position.set(10, 15, 8);
    mainLight.castShadow = true;
    mainLight.shadow.mapSize.width = 2048;
    mainLight.shadow.mapSize.height = 2048;
    mainLight.shadow.camera.left = -15;
    mainLight.shadow.camera.right = 15;
    mainLight.shadow.camera.top = 10;
    mainLight.shadow.camera.bottom = -10;
    mainLight.shadow.camera.near = 0.5;
    mainLight.shadow.camera.far = 50;
    scene.add(mainLight);

    const fillLight = new THREE.DirectionalLight(0x4466aa, 0.3);
    fillLight.position.set(-5, 5, -5);
    scene.add(fillLight);

    const waterLight = new THREE.PointLight(0x00aaff, 0.8, 10);
    waterLight.position.set(CONFIG.centerDistance / 2, -0.5, 0);
    scene.add(waterLight);
}

function setupGroundAndGrid() {
    const groundGeo = new THREE.PlaneGeometry(40, 30);
    const groundMat = new THREE.MeshStandardMaterial({
        color: 0x1a1a2e,
        roughness: 0.9,
        metalness: 0.1
    });
    const ground = new THREE.Mesh(groundGeo, groundMat);
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -1.5;
    ground.receiveShadow = true;
    scene.add(ground);

    const gridHelper = new THREE.GridHelper(30, 30, 0xe94560, 0x2a2a4a);
    gridHelper.position.y = -1.49;
    gridHelper.visible = showGrid;
    gridHelper.name = 'gridHelper';
    scene.add(gridHelper);
}

function createWaterwheelFrame() {
    const frameMat = new THREE.MeshStandardMaterial({
        color: 0x8b4513,
        roughness: 0.7,
        metalness: 0.3
    });

    const postGeo = new THREE.CylinderGeometry(0.08, 0.1, 3.5, 8);
    const posts = [
        [-0.3, 0.25, 0], [-0.3, 0.25, 0.8], [-0.3, 0.25, -0.8],
        [CONFIG.centerDistance + 0.3, 0.25, 0], [CONFIG.centerDistance + 0.3, 0.25, 0.8], [CONFIG.centerDistance + 0.3, 0.25, -0.8]
    ];
    posts.forEach(p => {
        const post = new THREE.Mesh(postGeo, frameMat);
        post.position.set(p[0], p[1], p[2]);
        post.castShadow = true;
        post.receiveShadow = true;
        scene.add(post);
    });

    const beamGeo = new THREE.BoxGeometry(CONFIG.centerDistance + 0.6, 0.12, 0.12);
    const beams = [
        [CONFIG.centerDistance / 2, 2.0, 0.8],
        [CONFIG.centerDistance / 2, 2.0, -0.8],
        [CONFIG.centerDistance / 2, 2.0, 0]
    ];
    beams.forEach(b => {
        const beam = new THREE.Mesh(beamGeo, frameMat);
        beam.position.set(b[0], b[1], b[2]);
        beam.castShadow = true;
        scene.add(beam);
    });

    const topBeamGeo = new THREE.BoxGeometry(0.8, 0.1, 1.8);
    [-0.3, CONFIG.centerDistance + 0.3].forEach(x => {
        const topBeam = new THREE.Mesh(topBeamGeo, frameMat);
        topBeam.position.set(x, 2.0, 0);
        topBeam.castShadow = true;
        scene.add(topBeam);
    });
}

function createSprockets() {
    const teethGeo = new THREE.CylinderGeometry(
        CONFIG.sprocketRadius + 0.04, CONFIG.sprocketRadius + 0.04, 0.15, CONFIG.sprocketTeeth * 2
    );
    const hubGeo = new THREE.CylinderGeometry(0.1, 0.1, 0.25, 16);
    const bodyGeo = new THREE.CylinderGeometry(CONFIG.sprocketRadius, CONFIG.sprocketRadius, 0.1, 32);

    const sprocketMat = new THREE.MeshStandardMaterial({
        color: 0x654321,
        roughness: 0.6,
        metalness: 0.4
    });
    const teethMat = new THREE.MeshStandardMaterial({
        color: 0x8b6914,
        roughness: 0.5,
        metalness: 0.5
    });

    drivingSprocket = new THREE.Group();
    const teeth1 = new THREE.Mesh(teethGeo, teethMat);
    teeth1.castShadow = true;
    drivingSprocket.add(teeth1);
    const body1 = new THREE.Mesh(bodyGeo, sprocketMat);
    body1.castShadow = true;
    drivingSprocket.add(body1);
    const hub1 = new THREE.Mesh(hubGeo, new THREE.MeshStandardMaterial({color: 0x333333, metalness: 0.8}));
    hub1.castShadow = true;
    drivingSprocket.add(hub1);

    for (let i = 0; i < CONFIG.sprocketTeeth; i++) {
        const angle = (i / CONFIG.sprocketTeeth) * Math.PI * 2;
        const toothGeo = new THREE.BoxGeometry(0.08, 0.06, 0.12);
        const tooth = new THREE.Mesh(toothGeo, teethMat);
        tooth.position.x = Math.cos(angle) * CONFIG.sprocketRadius;
        tooth.position.y = Math.sin(angle) * CONFIG.sprocketRadius;
        tooth.rotation.z = angle;
        tooth.castShadow = true;
        drivingSprocket.add(tooth);
    }

    drivingSprocket.rotation.y = Math.PI / 2;
    drivingSprocket.position.set(0, CONFIG.sprocketRadius, 0);
    scene.add(drivingSprocket);

    drivenSprocket = new THREE.Group();
    const drivenTeethGeo = new THREE.CylinderGeometry(
        CONFIG.drivenRadius + 0.04, CONFIG.drivenRadius + 0.04, 0.15, CONFIG.sprocketTeeth * 2
    );
    const drivenBodyGeo = new THREE.CylinderGeometry(CONFIG.drivenRadius, CONFIG.drivenRadius, 0.1, 32);

    const teeth2 = new THREE.Mesh(drivenTeethGeo, teethMat);
    teeth2.castShadow = true;
    drivenSprocket.add(teeth2);
    const body2 = new THREE.Mesh(drivenBodyGeo, sprocketMat);
    body2.castShadow = true;
    drivenSprocket.add(body2);
    const hub2 = new THREE.Mesh(hubGeo, new THREE.MeshStandardMaterial({color: 0x333333, metalness: 0.8}));
    hub2.castShadow = true;
    drivenSprocket.add(hub2);

    for (let i = 0; i < CONFIG.sprocketTeeth; i++) {
        const angle = (i / CONFIG.sprocketTeeth) * Math.PI * 2;
        const toothGeo = new THREE.BoxGeometry(0.08, 0.06, 0.12);
        const tooth = new THREE.Mesh(toothGeo, teethMat);
        tooth.position.x = Math.cos(angle) * CONFIG.drivenRadius;
        tooth.position.y = Math.sin(angle) * CONFIG.drivenRadius;
        tooth.rotation.z = angle;
        tooth.castShadow = true;
        drivenSprocket.add(tooth);
    }

    drivenSprocket.rotation.y = Math.PI / 2;
    drivenSprocket.position.set(CONFIG.centerDistance, CONFIG.drivenRadius, 0);
    scene.add(drivenSprocket);
}

function createChain() {
    const linkLength = CONFIG.chainLength / CONFIG.numLinks;
    const linkGeo = new THREE.BoxGeometry(linkLength * 0.8, 0.035, 0.06);
    const linkMat = new THREE.MeshStandardMaterial({
        color: 0x555555,
        roughness: 0.4,
        metalness: 0.7
    });
    const pinGeo = new THREE.CylinderGeometry(0.015, 0.015, 0.08, 8);
    const pinMat = new THREE.MeshStandardMaterial({
        color: 0x888888,
        metalness: 0.9
    });

    chainLinkMesh = new THREE.InstancedMesh(linkGeo, linkMat, CONFIG.numLinks);
    chainLinkMesh.castShadow = true;
    chainLinkMesh.receiveShadow = false;
    chainLinkMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    scene.add(chainLinkMesh);

    pinMesh = new THREE.InstancedMesh(pinGeo, pinMat, CONFIG.numLinks);
    pinMesh.castShadow = true;
    pinMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    scene.add(pinMesh);

    chainLinkDummy = new THREE.Object3D();
    updateChainPositions(0);
}

function createScrapers() {
    const plateGeo = new THREE.BoxGeometry(CONFIG.scraperWidth, CONFIG.scraperHeight, 0.02);
    const sideGeo = new THREE.BoxGeometry(CONFIG.scraperWidth, 0.02, CONFIG.scraperDepth);

    const scraperMat = new THREE.MeshStandardMaterial({
        color: 0x8b6914,
        roughness: 0.6,
        metalness: 0.2,
        side: THREE.DoubleSide
    });

    const merged = mergeScraperGeometries(plateGeo, sideGeo);

    scraperMesh = new THREE.InstancedMesh(merged, scraperMat, CONFIG.scraperCount);
    scraperMesh.castShadow = true;
    scraperMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    scene.add(scraperMesh);

    scraperDummy = new THREE.Object3D();
}

function mergeScraperGeometries(plateGeo, sideGeo) {
    const p = plateGeo.attributes.position.array.slice();
    const s = sideGeo.attributes.position.array.slice();

    const geo = new THREE.BufferGeometry();
    const totalLen = p.length + s.length;
    const positions = new Float32Array(totalLen);
    positions.set(p, 0);

    for (let i = 0; i < s.length; i += 3) {
        const sx = s[i], sy = s[i + 1], sz = s[i + 2];
        positions[p.length + i] = sx;
        positions[p.length + i + 1] = sy + CONFIG.scraperHeight / 2;
        positions[p.length + i + 2] = sz + CONFIG.scraperDepth / 2;
    }

    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.computeVertexNormals();

    const indices = [];
    const src = (g) => {
        const tri = [];
        const idx = g.index ? g.index.array : null;
        const count = idx ? idx.length / 3 : g.attributes.position.count / 3;
        for (let t = 0; t < count; t++) {
            if (idx) tri.push(idx[t*3], idx[t*3+1], idx[t*3+2]);
            else tri.push(t*3, t*3+1, t*3+2);
        }
        return tri;
    };
    const plateIdx = src(plateGeo);
    const sideIdx = src(sideGeo);
    const offset = plateGeo.attributes.position.count;
    for (let i of plateIdx) indices.push(i);
    for (let i of sideIdx) indices.push(i + offset);
    geo.setIndex(indices);
    return geo;
}

function getChainPathPosition(progress) {
    const upperLength = CONFIG.centerDistance;
    const lowerLength = CONFIG.centerDistance;
    const driveArcLength = Math.PI * CONFIG.sprocketRadius;
    const drivenArcLength = Math.PI * CONFIG.drivenRadius;
    const totalLength = upperLength + lowerLength + driveArcLength + drivenArcLength;

    let distance = progress * totalLength;

    if (distance < upperLength) {
        const t = distance / upperLength;
        return {
            x: t * CONFIG.centerDistance,
            y: CONFIG.sprocketRadius,
            z: 0,
            angle: 0
        };
    }
    distance -= upperLength;

    if (distance < drivenArcLength) {
        const t = distance / drivenArcLength;
        const angle = Math.PI / 2 + t * Math.PI;
        return {
            x: CONFIG.centerDistance + CONFIG.drivenRadius * Math.cos(angle),
            y: CONFIG.drivenRadius + CONFIG.drivenRadius * Math.sin(angle),
            z: 0,
            angle: t * Math.PI
        };
    }
    distance -= drivenArcLength;

    if (distance < lowerLength) {
        const t = 1 - distance / lowerLength;
        return {
            x: t * CONFIG.centerDistance,
            y: -CONFIG.sprocketRadius + (CONFIG.drivenRadius - CONFIG.sprocketRadius) * t,
            z: 0,
            angle: Math.PI
        };
    }
    distance -= lowerLength;

    const t = distance / driveArcLength;
    const angle = -Math.PI / 2 + t * Math.PI;
    return {
        x: CONFIG.sprocketRadius * Math.cos(angle),
        y: CONFIG.sprocketRadius + CONFIG.sprocketRadius * Math.sin(angle),
        z: 0,
        angle: Math.PI + t * Math.PI
    };
}

function updateChainPositions(rotation) {
    for (let i = 0; i < CONFIG.numLinks; i++) {
        const progress = (i / CONFIG.numLinks + rotation) % 1.0;
        const pos = getChainPathPosition(progress);

        const nextProgress = ((i + 1) / CONFIG.numLinks + rotation) % 1.0;
        const nextPos = getChainPathPosition(nextProgress);
        const dx = nextPos.x - pos.x;
        const dy = nextPos.y - pos.y;
        const linkAngle = Math.atan2(dy, dx);

        chainLinkDummy.position.set(pos.x, pos.y, pos.z);
        chainLinkDummy.rotation.set(0, linkAngle, 0);
        chainLinkDummy.updateMatrix();
        chainLinkMesh.setMatrixAt(i, chainLinkDummy.matrix);

        chainLinkDummy.rotation.x = Math.PI / 2;
        chainLinkDummy.rotation.y = 0;
        chainLinkDummy.updateMatrix();
        pinMesh.setMatrixAt(i, chainLinkDummy.matrix);
    }
    chainLinkMesh.instanceMatrix.needsUpdate = true;
    pinMesh.instanceMatrix.needsUpdate = true;
}

function updateScrapers(rotation) {
    const spacing = CONFIG.numLinks / CONFIG.scraperCount;
    for (let i = 0; i < CONFIG.scraperCount; i++) {
        const linkIndex = Math.floor(i * spacing) % CONFIG.numLinks;
        const progress = (linkIndex / CONFIG.numLinks + rotation) % 1.0;
        const pos = getChainPathPosition(progress);
        const nextProgress = ((linkIndex + 1) / CONFIG.numLinks + rotation) % 1.0;
        const nextPos = getChainPathPosition(nextProgress);
        const linkAngle = Math.atan2(nextPos.y - pos.y, nextPos.x - pos.x);

        if (pos.y < 0.3 && pos.y > -0.8) {
            scraperDummy.position.set(pos.x, pos.y - 0.02, 0);
            scraperDummy.rotation.set(0, linkAngle, 0);
            scraperDummy.updateMatrix();
            scraperMesh.setMatrixAt(i, scraperDummy.matrix);
            scraperMesh.setColorAt(i, new THREE.Color(0x8b6914));
        } else {
            scraperDummy.position.set(-9999, -9999, -9999);
            scraperDummy.scale.set(0, 0, 0);
            scraperDummy.updateMatrix();
            scraperMesh.setMatrixAt(i, scraperDummy.matrix);
            scraperDummy.scale.set(1, 1, 1);
        }

        if (pos.y < 0 && Math.random() < 0.1) {
            spawnWaterDroplet(pos.x, pos.y, pos.z);
        }
    }
    scraperMesh.instanceMatrix.needsUpdate = true;
    if (scraperMesh.instanceColor) scraperMesh.instanceColor.needsUpdate = true;
}

function createWaterTrough() {
    const troughGroup = new THREE.Group();

    const troughGeo = new THREE.BoxGeometry(CONFIG.centerDistance + 1, 0.8, 1.5);
    const troughMat = new THREE.MeshStandardMaterial({
        color: 0x5a4a3a,
        roughness: 0.9,
        side: THREE.DoubleSide
    });
    const trough = new THREE.Mesh(troughGeo, troughMat);
    trough.position.set(CONFIG.centerDistance / 2, -0.6, 0);
    trough.receiveShadow = true;
    troughGroup.add(trough);

    const waterGeo = new THREE.BoxGeometry(CONFIG.centerDistance + 0.8, 0.4, 1.3);
    const waterMat = new THREE.MeshStandardMaterial({
        color: 0x1a6ba0,
        transparent: true,
        opacity: 0.7,
        roughness: 0.1,
        metalness: 0.1
    });
    waterTrough = new THREE.Mesh(waterGeo, waterMat);
    waterTrough.position.set(CONFIG.centerDistance / 2, -0.4, 0);
    troughGroup.add(waterTrough);

    scene.add(troughGroup);
}

function createWaterParticles() {
    const particleCount = 300;
    const particleGeo = new THREE.BufferGeometry();
    const positions = new Float32Array(particleCount * 3);
    const velocities = new Float32Array(particleCount * 3);
    const sizes = new Float32Array(particleCount);
    const lifetimes = new Float32Array(particleCount);

    for (let i = 0; i < particleCount; i++) {
        positions[i * 3] = Math.random() * CONFIG.centerDistance;
        positions[i * 3 + 1] = -0.5 + Math.random() * 1.5;
        positions[i * 3 + 2] = (Math.random() - 0.5) * 1.2;

        velocities[i * 3] = (Math.random() - 0.5) * 0.5;
        velocities[i * 3 + 1] = -Math.random() * 2 - 0.5;
        velocities[i * 3 + 2] = (Math.random() - 0.5) * 0.3;

        sizes[i] = Math.random() * 0.04 + 0.015;
        lifetimes[i] = Math.random() * 3;
    }

    particleGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    particleGeo.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    const particleMat = new THREE.PointsMaterial({
        color: 0x5dade2,
        size: 0.03,
        transparent: true,
        opacity: 0.8,
        sizeAttenuation: true,
        blending: THREE.AdditiveBlending
    });

    const particles = new THREE.Points(particleGeo, particleMat);
    particles.userData = { velocities, lifetimes, count: particleCount };
    particles.name = 'waterParticles';
    waterParticles.push(particles);
    scene.add(particles);

    const splashGeo = new THREE.BufferGeometry();
    const splashCount = 100;
    const splashPositions = new Float32Array(splashCount * 3);
    const splashVelocities = new Float32Array(splashCount * 3);
    const splashLifetimes = new Float32Array(splashCount);
    const splashActive = new Uint8Array(splashCount);

    for (let i = 0; i < splashCount; i++) {
        splashPositions[i * 3] = -100;
        splashPositions[i * 3 + 1] = -100;
        splashPositions[i * 3 + 2] = -100;
        splashLifetimes[i] = 0;
        splashActive[i] = 0;
    }

    splashGeo.setAttribute('position', new THREE.BufferAttribute(splashPositions, 3));
    const splashMat = new THREE.PointsMaterial({
        color: 0x85c1e9,
        size: 0.02,
        transparent: true,
        opacity: 0.9,
        sizeAttenuation: true
    });

    const splash = new THREE.Points(splashGeo, splashMat);
    splash.userData = { velocities: splashVelocities, lifetimes: splashLifetimes, active: splashActive, count: splashCount, nextIndex: 0 };
    splash.name = 'splashParticles';
    waterParticles.push(splash);
    scene.add(splash);
}

function spawnWaterDroplet(x, y, z) {
    const splash = waterParticles.find(p => p.name === 'splashParticles');
    if (!splash) return;

    const position = splash.geometry.attributes.position;
    const { velocities, lifetimes, active, count, nextIndex } = splash.userData;

    for (let j = 0; j < 3; j++) {
        const idx = (nextIndex + j) % count;
        position.array[idx * 3] = x + (Math.random() - 0.5) * 0.2;
        position.array[idx * 3 + 1] = y + Math.random() * 0.2;
        position.array[idx * 3 + 2] = z + (Math.random() - 0.5) * 0.3;

        velocities[idx * 3] = (Math.random() - 0.5) * 1.5;
        velocities[idx * 3 + 1] = Math.random() * 2 + 1;
        velocities[idx * 3 + 2] = (Math.random() - 0.5) * 1;

        lifetimes[idx] = 1.0 + Math.random();
        active[idx] = 1;
    }
    splash.userData.nextIndex = (nextIndex + 3) % count;
    position.needsUpdate = true;
}

function updateWaterParticles(dt) {
    const gravity = -4.0;

    waterParticles.forEach(particles => {
        const position = particles.geometry.attributes.position;
        const { velocities, lifetimes, active, count } = particles.userData;

        for (let i = 0; i < count; i++) {
            if (particles.name === 'waterParticles') {
                velocities[i * 3 + 1] += gravity * dt;

                position.array[i * 3] += velocities[i * 3] * dt;
                position.array[i * 3 + 1] += velocities[i * 3 + 1] * dt;
                position.array[i * 3 + 2] += velocities[i * 3 + 2] * dt;

                if (position.array[i * 3 + 1] < -0.7) {
                    position.array[i * 3] = Math.random() * CONFIG.centerDistance;
                    position.array[i * 3 + 1] = 1.5 + Math.random() * 0.5;
                    position.array[i * 3 + 2] = (Math.random() - 0.5) * 1.2;
                    velocities[i * 3] = (Math.random() - 0.5) * 0.5;
                    velocities[i * 3 + 1] = -Math.random() * 1.5 - 0.5;
                    velocities[i * 3 + 2] = (Math.random() - 0.5) * 0.3;
                }

                if (position.array[i * 3] < 0 || position.array[i * 3] > CONFIG.centerDistance) {
                    position.array[i * 3] = Math.random() * CONFIG.centerDistance;
                }
            } else if (particles.name === 'splashParticles' && active[i]) {
                velocities[i * 3 + 1] += gravity * dt;

                position.array[i * 3] += velocities[i * 3] * dt;
                position.array[i * 3 + 1] += velocities[i * 3 + 1] * dt;
                position.array[i * 3 + 2] += velocities[i * 3 + 2] * dt;

                lifetimes[i] -= dt;
                if (lifetimes[i] <= 0 || position.array[i * 3 + 1] < -0.7) {
                    active[i] = 0;
                    position.array[i * 3] = -100;
                    position.array[i * 3 + 1] = -100;
                }
            }
        }
        position.needsUpdate = true;
    });

    if (waterTrough) {
        waterTrough.position.y = -0.4 + Math.sin(time * 2) * 0.008;
    }
}

function setupControls() {
    let isDragging = false;
    let prevX = 0, prevY = 0;
    let theta = Math.atan2(camera.position.z, camera.position.x - CONFIG.centerDistance / 2);
    let phi = Math.acos(camera.position.y / camera.position.length());
    let radius = camera.position.length();

    const canvas = document.getElementById('three-canvas');

    canvas.addEventListener('mousedown', e => {
        isDragging = true;
        prevX = e.clientX;
        prevY = e.clientY;
    });

    canvas.addEventListener('mouseup', () => { isDragging = false; });
    canvas.addEventListener('mouseleave', () => { isDragging = false; });

    canvas.addEventListener('mousemove', e => {
        if (!isDragging) return;
        const dx = e.clientX - prevX;
        const dy = e.clientY - prevY;
        theta -= dx * 0.01;
        phi = Math.max(0.1, Math.min(Math.PI - 0.1, phi - dy * 0.01));
        updateCameraPosition();
        prevX = e.clientX;
        prevY = e.clientY;
    });

    canvas.addEventListener('wheel', e => {
        e.preventDefault();
        radius = Math.max(5, Math.min(25, radius + e.deltaY * 0.01));
        updateCameraPosition();
    }, { passive: false });

    function updateCameraPosition() {
        const cx = CONFIG.centerDistance / 2;
        camera.position.x = cx + radius * Math.sin(phi) * Math.cos(theta);
        camera.position.y = radius * Math.cos(phi);
        camera.position.z = radius * Math.sin(phi) * Math.sin(theta);
        camera.lookAt(cx, 0.3, 0);
    }
}

function setupCanvasEvents() {
    document.getElementById('showChain').addEventListener('change', e => {
        showChain = e.target.checked;
        if (chainLinkMesh) chainLinkMesh.visible = showChain;
        if (pinMesh) pinMesh.visible = showChain;
        if (scraperMesh) scraperMesh.visible = showChain;
    });

    document.getElementById('showWater').addEventListener('change', e => {
        showWater = e.target.checked;
        waterParticles.forEach(p => p.visible = showWater);
        if (waterTrough) waterTrough.visible = showWater;
    });

    document.getElementById('showGrid').addEventListener('change', e => {
        showGrid = e.target.checked;
        const grid = scene.getObjectByName('gridHelper');
        if (grid) grid.visible = showGrid;
    });

    document.getElementById('speedRange').addEventListener('input', e => {
        animationSpeed = parseFloat(e.target.value);
    });
}

function onWindowResize() {
    const canvas = document.getElementById('three-canvas');
    camera.aspect = canvas.clientWidth / canvas.clientHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(canvas.clientWidth, canvas.clientHeight);
}

function animate() {
    requestAnimationFrame(animate);

    const dt = 1 / 60 * animationSpeed;
    time += dt;

    const rotationSpeed = 0.15 * animationSpeed;
    const rotation = (time * rotationSpeed) % 1.0;

    if (drivingSprocket) drivingSprocket.rotation.z = time * rotationSpeed * Math.PI * 2;
    if (drivenSprocket) drivenSprocket.rotation.z = -time * rotationSpeed * Math.PI * 2 * (CONFIG.sprocketRadius / CONFIG.drivenRadius);

    updateChainPositions(rotation);
    updateScrapers(rotation);
    updateWaterParticles(dt);

    renderer.render(scene, camera);
}

window.ChainPump3D = ChainPump3D;

window.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('three-canvas')) {
        ChainPump3D.init();
    }
});
