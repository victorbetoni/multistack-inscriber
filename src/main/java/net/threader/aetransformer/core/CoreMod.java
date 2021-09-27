package net.threader.aetransformer.core;

import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//CREDITS: https://github.com/CJcool06/PokeBus/blob/d65c4d97d8bc04530fb41a094271e08936c2f93c/src/main/java/io/github/cjcool06/pokebus/coremod/PokeBusCoreMod.java#L61

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("AppliedTransformer")
@IFMLLoadingPlugin.DependsOn("appliedenergistics2")
public class CoreMod implements IFMLLoadingPlugin {
    private File modFile = null;

    public CoreMod() {
        fixAndLoadAE();
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.aetransformer.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> map) {
        modFile = (File)map.get("coremodLocation");
        if (modFile == null) {
            modFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    private void fixAndLoadAE() {
        try {
            File modsFolder = new File(System.getProperty("user.dir"), "mods");
            if (!modsFolder.exists()) {
                return;
            }

            Collection<File> jars = FileUtils.listFiles(modsFolder, new String[]{"jar"}, false);
            File ae = null;
            for (File jar : jars) {
                ZipInputStream zip = new ZipInputStream(new FileInputStream(jar));
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    zip.closeEntry();
                    if (entry.getName().equals("appeng/core/AppEng.class")) {
                        ae = jar;
                        break;
                    }
                }
                zip.close();
                if (ae != null) break;
            }

            if (ae == null) {
                System.out.println("Pixelmon's jar cannot be found, the program will not continue.");
                return;
            }

            if (!CoreModManager.getReparseableCoremods().contains(ae.getName())) {
                ((LaunchClassLoader) this.getClass().getClassLoader()).addURL(ae.toURI().toURL());
                CoreModManager.getReparseableCoremods().add(ae.getName());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}