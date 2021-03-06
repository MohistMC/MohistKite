package com.mohistmc.kite;

import com.mohistmc.kite.services.MixinService;
import com.google.common.io.ByteStreams;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

public final class MohistKiteAgent {
    public final static Logger LOGGER = MohistKiteLogger.getLogger(null);
    private static boolean initialized;
    private static Instrumentation instrumentation;
    private static String obcVersion, obcClassName;
    private final static Remapper remapper = new Remapper() {
        @Override
        public String map(String name) {
            if (!name.startsWith("org/bukkit/craftbukkit/Main") && !name.startsWith("org/bukkit/craftbukkit/libs/") &&
                    name.startsWith("org/bukkit/craftbukkit/") && !name.startsWith("org/bukkit/craftbukkit/v1_")) {
                if (obcVersion == null) throw new IllegalStateException("Cannot detect minecraft version!");
                return obcClassName + name.substring(23);
            }
            return super.map(name);
        }
    };
    private static Path serverJar;

    @NotNull
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    @SuppressWarnings("unused")
    @NotNull
    public static String getReleaseVersion() {
        return obcVersion;
    }

    @SuppressWarnings("unused")
    @Nullable
    public static Path getServerJar() {
        return serverJar;
    }

    @Nullable
    public static InputStream getResourceAsStream(String name) {
        return ClassLoader.getSystemResourceAsStream(name);
    }

    @Nullable
    public static InputStream getClassAsStream(String name) {
        return getResourceAsStream(name.replace('.', '/') + ".class");
    }

    public static byte[] getClassAsByteArray(String name) throws IOException {
        try (InputStream is = getClassAsStream(name)) {
            if (is == null) throw new FileNotFoundException("Class not found: " + name);
            return ByteStreams.toByteArray(is);
        }
    }

    private static void initMohistKite(Instrumentation instrumentation) {
        Package pkg = MohistKiteAgent.class.getPackage();
        LOGGER.info(pkg.getImplementationTitle() + " version: " + pkg.getImplementationVersion() +
                " (" + pkg.getImplementationVendor() + ")");
        System.setProperty("mixin.env.remapRefMap", "true");
        MohistKiteAgent.instrumentation = instrumentation;
        instrumentation.addTransformer(new Transformer(), true);
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
        System.setProperty("papershelled.enable", "true");
    }

    public static void premain(String arg, Instrumentation instrumentation) {
        System.setProperty("mixin.hotSwap", "true");
        initMohistKite(instrumentation);
        MixinAgent.premain(arg, instrumentation);
    }

    public static void agentmain(String arg, Instrumentation instrumentation) {
        initMohistKite(instrumentation);
        MixinAgent.agentmain(arg, instrumentation);
    }

    @SuppressWarnings("unused")
    public static void init() throws Throwable {
        initialized = true;
        MohistKite.init();
        MohistKiteLogger.restore();
    }

    private static byte[] inject(byte[] arr, String method0, String clazz, String method1) {
        ClassReader cr = new ClassReader(arr);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return method0.equals(name) ? new MethodVisitor(api, mv) {
                    @Override
                    public void visitCode() {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazz, method1, "()V", false);
                        super.visitCode();
                    }
                } : mv;
            }
        }, ClassReader.SKIP_DEBUG);
        return cw.toByteArray();
    }

    private static byte[] relocate(byte[] arr) {
        ClassReader cr = new ClassReader(arr);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(Opcodes.ASM9, cw, remapper) {
        }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private final static class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] data) {
            if ("io/papermc/paperclip/Paperclip".equals(className)) {
                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        MethodVisitor v = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return name.equals("main") ? new MethodVisitor(Opcodes.ASM9) {
                            /**
                             * What I want is as follows:
                             * {@code
                             *
                             * public static void main(String[] args){
                             *     com.mohistmc.kite.launcher.Launcher.launch(args);
                             * }
                             *
                             * }
                             * And this is the ASM way to make it.
                             */
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label l0 = new Label();
                                v.visitLabel(l0);
                                v.visitLineNumber(0, l0);//Trick JVM
                                v.visitVarInsn(Opcodes.ALOAD, 0);
                                v.visitMethodInsn(Opcodes.INVOKESTATIC, "com/mohistmc/kite/launcher/Launcher", "launch", "([Ljava/lang/String;)V", false);
                                Label l1 = new Label();
                                v.visitLabel(l1);
                                v.visitLineNumber(0, l1);
                                v.visitInsn(Opcodes.RETURN);
                                Label l2 = new Label();
                                v.visitLabel(l2);
                                v.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l2, 0);
                                v.visitMaxs(1, 1);
                                v.visitEnd();
                            }
                        } : v;
                    }
                }, ClassReader.EXPAND_FRAMES);
                return cw.toByteArray();
            } else if ("org/bukkit/craftbukkit/Main".equals(className)) {
                data = inject(data, "main", "com/mohistmc/kite/MohistKiteAgent", "init");
                URL url = Objects.requireNonNull(loader.getResource("org/bukkit/craftbukkit/Main.class"));
                try {
                    serverJar = Paths.get(new URI(url.getFile().split("!", 2)[0]));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile()) {
                    Enumeration<JarEntry> itor = jar.entries();
                    while (itor.hasMoreElements()) {
                        String name = itor.nextElement().getName();
                        if (name.startsWith("org/bukkit/craftbukkit/v")) {
                            obcVersion = name.substring(23).split("/", 2)[0];
                            obcClassName = "org/bukkit/craftbukkit/" + obcVersion + "/";
                            LOGGER.info("Detected minecraft version: " + obcVersion);
                            break;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (className.startsWith("org/bukkit/craftbukkit/v1_") && className.endsWith("/CraftServer")) {
                data = inject(data, "loadPlugins", "com/mohistmc/kite/MohistKite", "injectPlugins");
                if (obcVersion == null) {
                    obcVersion = className.substring(23).split("/", 2)[0];
                    obcClassName = "org/bukkit/craftbukkit/" + obcVersion + "/";
                    LOGGER.info("Detected minecraft version: " + obcVersion);
                }
            }
            if (!initialized) return data;
            IMixinTransformer transformer = MixinService.getTransformer();
            return transformer == null ? null : transformer.transformClass(MixinEnvironment.getDefaultEnvironment(),
                    className.replace('/', '.'), relocate(data));
        }
    }
}
