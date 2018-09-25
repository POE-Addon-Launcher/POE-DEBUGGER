import Data.Launcher_Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DebuggerUi implements Initializable
{

    @FXML
    private Button magicbutton;

    @FXML
    private Button bX;

    @FXML
    private TextArea dbgTxt;

    @FXML
    private ProgressBar progressbar;

    /**
     * Stages:
     * 0 - Check Appdata for launcher files. {If Nothing -> Ask user to manually clean}
     * 1 - Attempt to parse launcher_data.pal {InstallDir, LauncherLocation, Version}
     * 2 - Attempt to parse poe_paths.pal {Check PoE Exes}
     * 3 - Check Core Folder for files that should be in there.
     * 4 - Check Addons Folder
     * 5 - Check {core_settings.pal} -> no exist probably never launched bxx.jar
     * 6 - Attempt to run the launcher and get the output.
     * 7 - Attempt to run bxx.jar -> get output
     * 8 - Ask user if everything works -> exit
     * 9 - Clean all folders -> Ask for permission
     * 10 -> Download most recent Launcher and tell user to run it.
     */
    private int debug_stage = 0;
    private String total_text = "PAL DEBUGGER REPORT\n\n";
    private StringBuilder stringBuilder = new StringBuilder();

    public void initialize(URL location, ResourceBundle resources)
    {
        stringBuilder.append(total_text);
        stringBuilder.append("Launched Debugger\n\n");
        add("Click start to start the debugger.");
    }

    private ArrayList<String> dbg = new ArrayList<>();

    public void add(String s)
    {
        stringBuilder.append(s);
        System.out.println(s);
        Platform.runLater(() -> dbgTxt.setText(dbgTxt.getText() + s + "\n"));
        dbg.add(s + "\n");
        stringBuilder.append("\n");
        Platform.runLater(() ->
        {
            dbgTxt.selectPositionCaret(dbgTxt.getLength());
            dbgTxt.deselect();
        });

    }

    public void magic(ActionEvent actionEvent)
    {
        switch (debug_stage)
        {
            case 0 : checkAppData(); break;
            case 1 : parseLauncher_Data(); break;
            case 2 : parsePoePaths(); break;
            case 3 : parseCheckCoreFolder(); break;
            case 4 : checkAddonsFolder(); break;
            case 5 : checkCoreSettings(); break;
            case 6 : runlauncher(); break;
            case 7 : runCore(); break;
            case 8 : isWorking(); break;
            case 9 : cleanAllFolders(); break;
            case 10 : cleanAllFolders2(); break;
            case 11 : downloadLauncher(); break;
            case 12 : exit(); break;
        }
    }

    public void recursiveDelete(File f)
    {
        if (f.isDirectory())
        {
            if (Objects.requireNonNull(f.list()).length == 0)
            {
                f.delete();
                System.out.println("[DELETED] " + f.getPath());
            }
            else
            {
                File[] files = f.listFiles();

                for (File _f : files)
                {
                    recursiveDelete(_f);
                }

                if (Objects.requireNonNull(f.list()).length == 0)
                {
                    f.delete();
                    System.out.println("[DELETED] " + f.getPath());
                }
            }
        }
        else
        {
            f.delete();
            System.out.println("[DELETED] " + f.getPath());
        }
    }

    private void cleanAllFolders2()
    {
        add("--------------------------\nWARNING2: Clicking continue will delete \nthe following folders and their files:\n" +
                LOCAL_PAL_FOLDER + "\n" + launcher_data.getInstall_dir());
        debug_stage++;
    }

    private void downloadLauncher()
    {
        Runnable r = () ->
        {
            if (!launcher_data.getInstall_dir().equals(LOCAL_PAL_FOLDER))
            {
                recursiveDelete(new File(launcher_data.getInstall_dir()));
            }
            recursiveDelete(new File(LOCAL_PAL_FOLDER));

            add("Please re-download the launcher and reinstall PAL.");
            if (Desktop.isDesktopSupported())
            {
                try
                {
                    Desktop.getDesktop().browse(new URI("https://github.com/POE-Addon-Launcher/PoE-Addon-Launcher/releases"));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                catch (URISyntaxException e)
                {
                    e.printStackTrace();
                }
            }
            Platform.runLater(() -> progressbar.setProgress(1));
            add("Debugger has finished\nClick continue to generate LOG FILE");
            debug_stage++;
        };
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();

    }

    private void cleanAllFolders()
    {
        add("[RUN] cleanAllFolders()");
        add("-------------------------------\nWARNING IF YOU CLICK CONTINUE\nTHE DEBUGGER WILL UNINSTALL PAL\nAND ALL OF IT'S FOLDERS\nCLICK X TO EXIT.");
        debug_stage++;
    }

    private void isWorking()
    {
        add("[RUN] -> isWorking()");
        add("****************************************\nIn the previous step dit Core launch normally?\nIf YES Click the X button.\nOtherwise Click Continue.");
        debug_stage++;
    }

    private void runCore()
    {
        add("[RUN] -> runCore()");
        add("Program will now attempt to run Core");


        Runnable r = () ->
        {
                try
                {
                    Process p = Runtime.getRuntime().exec("java -jar \"" + getMostRecentBuild(new File(launcher_data.getInstall_dir() + File.separator + "Core")) + "\"");
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    String line;
                    while ((line = in.readLine()) != null)
                    {
                        add(line);
                    }
                }
                catch (IOException e)
                {
                    add("[EXIT] -> IOException");
                    add(e.toString());
                    exit();
                }
        };
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
        next();
        add("==========\nFeel free to close PAL\n after it has launched.\nThis Step is complete.");
    }

    private File getMostRecentBuild(File f)
    {
        File[] files = f.listFiles();
        File most_recent_jar = new File("b0.jar");
        for (File file : files)
        {
            if (file.getName().contains(".jar"))
            {
                if (isNewer(most_recent_jar.getName(), file.getName()))
                {
                    most_recent_jar = file;
                }
            }
        }
        return most_recent_jar;
    }

    private boolean isNewer(String _old, String _new)
    {
        _old = _old.replace("b", "");
        _old = _old.replace(".jar", "");
        _new = _new.replace("b", "");
        _new =_new.replace(".jar", "");

        int num_old = Integer.parseInt(_old);
        int num_new = Integer.parseInt(_new);

        return num_new > num_old;
    }

    private void runlauncher()
    {
        add("[RUN] -> runlauncher()");

        try
        {
            Process p = Runtime.getRuntime().exec("java -jar \"" + launcher_data.getLauncher_location().subSequence(1, launcher_data.getLauncher_location().length()) + "\"");
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            while ((line = in.readLine()) != null)
            {
                add(line);
            }
        }
        catch (IOException e)
        {
            add("[EXIT] -> IOException");
            add(e.toString());
            exit();
        }
        next();
    }

    private void checkCoreSettings()
    {
        add("[RUN] -> checkCoreSettings()");
        File f = new File(LOCAL_PAL_FOLDER + File.separator + "core_settings.pal");
        if (f.exists())
        {
            add("core_settings.pal exists!");
        }
        else
        {
            add("[SEVERE] No core_settings.pal, this means you've never ran the core program, this indicates launcher may not have worked for you.");
        }
        next();
        add("Clicking continue will attempt to run the Launcher.");
    }

    /**
     * Checks if every folder has an .nfo in it.
     */
    private void checkAddonsFolder()
    {
        add("[RUN] -> checkAddonsFolder()");
        File install_dir = new File(launcher_data.getInstall_dir());
        File dir = new File(install_dir + File.separator + "Addons");
        add("Checking: " + dir.getPath());
        for (File f : dir.listFiles())
        {
            add("-> " + f.getName());
            if (f.isDirectory())
            {
                if (f.getName().equals("temp"))
                {
                    add("found temp folder.");
                }
                else
                {
                    for (File f_ : f.listFiles())
                    {
                        if (f_.getName().equals("nfo.pal"))
                        {
                            add("\t-> checks out.");
                        }
                    }
                }
            }
            else
            {
                add("[ERROR] Non-folder detected in Addons folder this isn't supposed to be here.");
            }
        }
        next();
    }

    private void parseCheckCoreFolder()
    {
        add("[RUN] -> parseCheckCoreFolder()");
        File install_dir = new File(launcher_data.getInstall_dir());
        if (install_dir.exists())
        {
            File dir = new File(install_dir + File.separator + "Core");
            add("PRINTING CORE DIR:");
            printDir(dir);

            add("Checking dir for oddities...");
            for (File f : dir.listFiles())
            {
                add("-> " + f.getName());
                if (f.getName().matches("b(\\d){0,9999999}(.jar)"))
                {
                    add("File checks out.");
                }
                else
                {
                    add("[ERROR] FILE DOESN'T BELONG HERE OR NAME IS WRONG.");
                }
            }
            next();
        }
        else
        {
            add("[EXIT] Install dir doesn't exist anymore!");
            add("You can fix this by deleting your launcher_data.pal file in: " + LOCAL_PAL_FOLDER);
            exit();
        }
    }

    private void parsePoePaths()
    {
        add("[RUN] -> parsePoePaths()");
        add("Attempting to parse: " + LOCAL_PAL_FOLDER + File.separator + "poe_paths.pal");
        File f = new File(LOCAL_PAL_FOLDER + File.separator + "poe_paths.pal");
        if (f.exists())
        {
            add("poe_paths.pal exists.");
            add("parsing...");
            ArrayList<String> paths = readPoePaths();
            for (String s : paths)
            {
                add("Checking: " + s);
                File f_ = new File(s);
                if (!f_.exists())
                {
                    add("[WARNING] This PoE Path isn't valid anymore!");
                }
                else
                {
                    add("-> Valid path");
                }
            }
            add("parsePoePaths [PASSED]");
            next();
        }
        else
        {
            add("[EXIT] poe_paths.pal doesn't exist!");
            exit();
        }
    }

    public ArrayList<String> readPoePaths()
    {
        ArrayList<String> poe_paths = new ArrayList<>();
        String[] poe_paths_array;

        File poe_paths_pal = new File(LOCAL_PAL_FOLDER + File.separator + "poe_paths.pal");
        if (poe_paths_pal.exists())
        {
            ObjectMapper objectMapper = new ObjectMapper();
            try
            {
                poe_paths_array = objectMapper.readValue(poe_paths_pal, String[].class);
                for (String s : poe_paths_array)
                {
                    poe_paths.add(s);
                }
                return poe_paths;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }
        return poe_paths;
    }

    private Path saveCurrText()
    {
        Path file = Paths.get(System.getenv("LOCALAPPDATA") + File.separator + "PAL_DEBUGGER_LOG.txt");
        try
        {
            Files.write(file, dbg.subList(0, dbg.size()), Charset.forName("UTF-8"));
            return file;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @FXML
    private void exit()
    {
        // Create a file with the resulting text.
        Path file = saveCurrText();
        // Open the folder containing it.
        try
        {
            Process p = new ProcessBuilder("explorer.exe", "/select," + file.toString()).start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.exit(debug_stage);
    }

    private void parseLauncher_Data()
    {
        add("[RUN] -> parseLauncher_Data()");
        add("Parsing file...");
        boolean result = readLauncherData();
        if (result)
        {
            add("Checking data...");
            if (launcher_data.getLauncher_location() != null)
            {
                File launcher_loc = new File(launcher_data.getLauncher_location());
                add("Checking if Launcher exists...");
                if (launcher_loc.exists())
                {
                    add("Launcher is valid, continuing...");
                    add("Launcher version is: " + launcher_data.getVersion());
                    add("Checking install directory...");
                    if (launcher_data.getInstall_dir() != null)
                    {
                        File install_dir = new File(launcher_data.getInstall_dir());
                        if (install_dir.exists())
                        {
                            add("Install directory exists!");
                            add("parseLauncher_Data() [PASSED]");
                            add("\n\nprinting directory structure:");

                            add("PRINTING CORE DIR:");
                            printDir(new File(install_dir + File.separator + "Core"));

                            add("\n\nPRINTING WHOLE DIR:");
                            Runnable r = () ->
                            {
                                printDir(install_dir);
                                add("Press Continue to continue!");
                            };
                            Thread t = new Thread(r);
                            t.start();

                            next();
                        }
                        else
                        {
                            add("[EXIT] Install dir doesn't exist anymore!");
                            add("You can fix this by deleting your launcher_data.pal file in: " + LOCAL_PAL_FOLDER);
                            exit();
                        }
                    }
                    else
                    {
                        add("[EXIT] Install dir isn't set!");
                        add("You can fix this by deleting your launcher_data.pal file in: " + LOCAL_PAL_FOLDER);
                        exit();
                    }
                }
                else
                {
                    add("[EXIT] Launcher_Location isn't valid.");
                    add("You can fix this by deleting your launcher_data.pal file in: " + LOCAL_PAL_FOLDER);
                    exit();
                }
            }
        }
        else
        {
            add("[EXIT] Parsing Failed.");
            exit();
        }
    }

    private void printDir(File f)
    {
        if (f.isDirectory())
        {
            if (Objects.requireNonNull(f.list()).length == 0)
            {
                add(f.getPath());
            }
            else
            {
                File[] files = f.listFiles();

                for (File _f : files)
                {
                    printDir(_f);
                }

                if (Objects.requireNonNull(f.list()).length == 0)
                {
                    add(f.getPath());
                }
            }
        }
        else
        {
            add(f.getPath());
        }
    }


    private Launcher_Data launcher_data;
    /**
     * Read launcher data.
     */
    public boolean readLauncherData()
    {
        add("[RUN] -> readLauncherData()");
        File f = new File(LOCAL_PAL_FOLDER + File.separator + "launcher_data.pal");
        if (f.exists())
        {
            ObjectMapper objectMapper = new ObjectMapper();
            try
            {
                launcher_data = objectMapper.readValue(f, Launcher_Data.class);
                add("launcher_data has been parsed!");
                return true;
            }
            catch (IOException e)
            {
                add("[EX] IOException" + e.toString());
            }
        }
        return false;
    }

    private void next()
    {
        debug_stage++;
        progressbar.setProgress(debug_stage / 11d);
        add("Click continue to continue with the debugging.");
        Platform.runLater(() -> magicbutton.setText("continue"));
        saveCurrText();
    }

    private String LOCAL_PAL_FOLDER;

    private void checkAppData()
    {
        add("[RUN] -> checkAppData()");
        add("Getting %LOCALAPPDATA%");
        String local_appdata = System.getenv("LOCALAPPDATA");
        add(local_appdata);

        LOCAL_PAL_FOLDER = local_appdata + File.separator + "PAL";

        add("Checking for: " + local_appdata + File.separator + "PAL" + File.separator + "launcher_data.pal");
        File f = new File(local_appdata + File.separator + "PAL" + File.separator + "launcher_data.pal");

        if (f.exists())
        {
            add("checkAppData() [PASSED]");
            next();
        }
        else
        {
            add(local_appdata + File.separator + "launcher_data.pal" + " !!! DOESN'T EXIST!");
            add("You do not have a launcher_data.pal file, download the newest launcher please.");
            debug_stage = 11;
            exit();
        }
    }
}
