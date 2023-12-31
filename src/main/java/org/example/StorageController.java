package org.example;

import api.longpoll.bots.exceptions.VkApiException;
import com.yandex.disk.rest.Credentials;
import com.yandex.disk.rest.ResourcesArgs;
import com.yandex.disk.rest.RestClient;
import com.yandex.disk.rest.exceptions.ServerException;
import com.yandex.disk.rest.exceptions.ServerIOException;
import com.yandex.disk.rest.json.Resource;
import org.example.utils.Listener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class StorageController {
    private static final Listener listener = new Listener();
    private static final Properties properties = Main.properties;
    private static RestClient restClient;
    private static final String mainPath = properties.getProperty("location_to_sync");

    private static ResourcesArgs.Builder build;
    public static List<Resource> info;

    public static String chosenPath = "";

    private StorageController(){}

    public static void init(){
        Credentials credentials = new Credentials("fedor", properties.getProperty("yandex_token"));
        restClient = new RestClient(credentials);
        build = new ResourcesArgs.Builder();
        build.setPath("/");
        info = new ArrayList<>();
    }
    public static void createSubdirectories(String path) {
        String tempPath = chosenPath.contains("/") ? chosenPath.substring(0, chosenPath.length() - 1) : chosenPath;

        if(path.contains("/")) {
            String newPath = path.substring(0, path.lastIndexOf("/"));
            List<String> arr = List.of(newPath.split("/"));
            StringBuilder fullPath = new StringBuilder(mainPath + "\\" + tempPath);
            if (!arr.isEmpty()) {
                for (String elem : arr) {
                    fullPath.append(elem);
                    File dir = new File(fullPath.toString());
                    if(!dir.exists()) {
                        dir.mkdir();
                    }
                    fullPath.append("/");
                }
            }
        }

    }
    public static long getYandexFreeStorageSize() throws ServerIOException, IOException {
        return restClient.getDiskInfo().getTotalSpace();
    }
    public static long getYandexUsedStorageSize() throws ServerIOException, IOException {
        return restClient.getDiskInfo().getUsedSpace();
    }

    public static String getAllStorageInfo() throws ServerIOException, IOException, VkApiException {
        return "Занятого места на я.диске около: " + Math.round(getYandexUsedStorageSize() / Math.pow(1024, 2))+ "мб\n"
                + "Свободного места на я.диске около: " + getYandexFreeStorageSize() / Math.pow(1024, 3) + "гб\n"
                + "Авто очистка корзины после загрузки файлов на лок. хранилище: " + ((StorageController.getAutoCleanUpValue() ? "включено" : "отключено") + "\n"
                + "Выбранный путь для сохранения файлов: " + (chosenPath.isEmpty() ? "/" : chosenPath));
    }
    public static void handleSaveFromYandex() throws ServerException, IOException, InterruptedException {
        String path;
        List<Resource> needDownloadFiles = getDiskInfo();
        while (!needDownloadFiles.isEmpty()) {
            for (Resource res : needDownloadFiles) {
                path = res.getPath().getPath();

                if (!res.isDir() && path.split("/").length > 2) {
                    createSubdirectories(path);
                }
                saveFileFromYandex(path);
            }
            deleteFromYandex(needDownloadFiles);
            needDownloadFiles = getDiskInfo();
        }
        chosenPath = "";
    }
    private static void saveFileFromYandex(String path) throws InterruptedException {
        File file = new File(mainPath + "\\" + chosenPath + path);
        if (file.exists())
            file.delete();
        String tempPath = (chosenPath.length() > 1) ? chosenPath.substring(0, chosenPath.length() - 1) : chosenPath;

        boolean pass = false;

        while (!pass) {
            try {
                restClient.downloadFile(path,
                        new File(mainPath + "\\" + tempPath + path),
                        listener);
                pass = true;
            } catch (Exception ignored) {
                //System.out.println("Файл не скачался. Пробую ещё раз");
                Thread.sleep(500);
            }
        }

    }
    public static void deleteFromYandex() throws ServerIOException, IOException {
        deleteFromYandexMain();
    }
    private static void deleteFromYandex(List<Resource> list) throws IOException {
        String path;
        for(Resource res : list) {
            path = res.getPath().getPath();
            deleteFileFromYandex(path);
        }
    }
    private static void deleteFromYandexMain() throws ServerIOException, IOException {
        String path;
        List<Resource> list = restClient.getResources(build.build()).getResourceList().getItems();
        for(Resource res : list) {
            path = res.getPath().getPath();
            deleteFileFromYandex(path);
        }
    }
    private static boolean autoCleanUp = true;
    public static void controlYandexAutoCleanUpTrash(){
        autoCleanUp = !autoCleanUp;
    }

    public static boolean getAutoCleanUpValue() {
        return autoCleanUp;
    }

    public static void deleteFromYandexTrash() throws ServerIOException, IOException {
        String path;
        List<Resource> list = restClient.getTrashResources(build.build()).getResourceList().getItems();
        for(Resource res : list) {
            path = res.getPath().getPath();
            restClient.deleteFromTrash(path);
        }
    }
    private static void deleteFileFromYandex(String path) throws IOException {
        try {
            restClient.delete(path, autoCleanUp);
        } catch (ServerIOException ignored) {
            //System.out.println("Не удалось удалить, видимо файла не существует");
        }
    }
    public static List<Resource> getDiskInfo() throws ServerIOException, IOException {
        return restClient.getLastUploadedResources(build.build()).getItems();
    }

    public static List<String> findSimilarFiles(List<String> names) {
        List<String> list = new ArrayList<>();
        List<String> result = new ArrayList<>();
        File directory = new File(mainPath);
        for (String name: names) {
            if (directory.isDirectory()) {
                getFilesAndFoldersList(directory, list);
                for (String file : list) {
                    file = file.substring(mainPath.length());
                    if (file.contains(name) && !result.contains(file))
                        result.add(file);
                }
            }
        }
        return result;

    }
    private static void getFilesAndFoldersList(File directory, List<String> filesAndFolders) {
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                filesAndFolders.add(file.getAbsolutePath());
                getFilesAndFoldersList(file, filesAndFolders);
            } else {
                filesAndFolders.add(file.getAbsolutePath());
            }
        }
    }

    public static List<String> findSimilarPath(String name) {
        List<String> list = new ArrayList<>();
        List<String> result = new ArrayList<>();
        File directory = new File(mainPath);
        if (directory.isDirectory()) {
            getFoldersList(directory, list, mainPath);
            for (String folder : list) {
                folder = folder.substring(mainPath.length());
                if(folder.contains(name))
                    result.add(folder);
            }
        }
        return result;

    }
    private static void getFoldersList(File directory, List<String> folders, String path) {
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String folderPath = path + file.getName() + "/";
                folders.add(folderPath);
                getFoldersList(file, folders, folderPath);
            }
        }
    }
    public static List<String> uploadPaths;
    public static void uploadFilesToYandex() throws ServerException, IOException {
        for (String path: uploadPaths) {
            uploadFile(path);
        }
        uploadPaths = null;
    }
    public static void deleteFromStorage() {
        for (String path: uploadPaths) {
            deleteFileFromStorage(path);
        }
        uploadPaths = null;
    }
    private static void deleteFileFromStorage(String path) {
        File file = new File(mainPath + path);
        if(file.exists()) {
            if(file.isDirectory()) {
                deleteDir(file);
            }
            file.delete();
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if(files.length > 0) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteDir(file);
                file.delete();
            }
        }
    }

    private static List<File> getFilesArray(List<File> files) {
        List<File> res = new ArrayList<>();
        for(File file: files) {
            if(file.isFile())
                res.add(file);
        }
        return res;
    }

    private static List<File> getDirsArray(List<File> files) {
        List<File> res = new ArrayList<>();
        for(File file: files) {
            if(file.isDirectory())
                res.add(file);
        }
        return res;
    }

    private static void makeDirsOnYandex(String path) {
        List<String> dirs = List.of(path.split("\\\\"));
        StringBuilder fullPath = new StringBuilder();
        for (String dir: dirs) {
            fullPath.append(dir);
            try {
                restClient.makeFolder(fullPath.toString());
            } catch (Exception ignored) {
                //System.out.println("Возможно папка существует" + fullPath + " " + ignored);
            }
            fullPath.append("/");
        }
    }

    private static void uploadFile(String path) throws ServerException, IOException {
        String fullPath = mainPath + path;
        File tempFile = new File(fullPath);


        if(tempFile.isDirectory() && !List.of(tempFile.listFiles()).isEmpty()) {

            List<File> listOfFiles = List.of(tempFile.listFiles());
            List<File> files;
            List<File> dirs;

            String tempPath = tempFile.getPath().substring(mainPath.length());
            makeDirsOnYandex(tempPath);

            files = getFilesArray(listOfFiles);
            dirs = getDirsArray(listOfFiles);
            for(File file: files) {
                //System.out.println("Загрузка файла " + file.getPath());
                restClient.uploadFile(restClient.getUploadLink(file.getAbsolutePath().substring(mainPath.length()).replace("\\", "/"), true),
                true,
                        file,
                        listener);
            }
            for(File dir: dirs) {
                //System.out.println("Dirs for " + dir.getPath());
                uploadFile(dir.getAbsolutePath().substring(mainPath.length()));
            }
        } else {
            restClient.uploadFile(restClient.getUploadLink(path.substring(path.lastIndexOf("\\")).replace("\\", "/"), true),
                    true,
                    tempFile,
                    listener);
        }
    }
}
