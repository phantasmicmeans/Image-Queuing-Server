package com.kt.narle.imageserver.storage;

import com.kt.narle.imageserver.exception.StorageException;
import com.kt.narle.imageserver.exception.StorageFileNotFoundException;
import com.kt.narle.imageserver.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public FileSystemStorageService(StorageProperties properties) {
        this.rootLocation = Paths.get(properties.getLocation());
    }

    @PostConstruct
    public void init() {
        try {
            if(!Files.isDirectory(rootLocation))
                Files.createDirectories(rootLocation);
        }
        catch (IOException e) {
            throw new StorageException("디렉토리 초기화를 할 수 없습니다.", e);
        }
    }

    @Override
    public void store(MultipartFile file) {
        String filename = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                throw new StorageException("빈 파일입니다." + filename);
            }
            if (filename.contains("..")) {
                // This is a security check
                throw new StorageException(
                        "경로를 확인하세요" + filename);
            }
            if(filename.toUpperCase().endsWith("JPEG") || !filename.toUpperCase().endsWith("JPG") || filename.toUpperCase().endsWith("PNG")) {
                try (FileInputStream inputStream = (FileInputStream)file.getInputStream()) {
                    Files.copy(inputStream, this.rootLocation.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new StorageException("파일을 저장할 수 없습니다. " + filename, e);
                }
            }else
                throw new StorageException("지원하지 않는 확장자를 가진 파일입니다");
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootLocation, 1)
                .filter(path -> !path.equals(this.rootLocation))
                .map(this.rootLocation::relativize);
        }
        catch (IOException e) {
            throw new StorageException("저장된 파일을 읽어올 수 없습니다.", e);
        }

    }

    @Override
    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            else {
                throw new StorageFileNotFoundException(
                        "Could not read file: " + filename);

            }
        }
        catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("파일을 읽을 수 없습니다. " + filename, e);
        }
    }

    public void delete(String filename) {
        try {
            Path path = load(filename);
            Files.deleteIfExists(path);
        }catch(IOException e) {
            throw new StorageException("존재하지 않는 파일입니다");
        }
    }
    @Override
    public List<String> getLines(String filename) {

        List<String> retData = null;
        Path file = load(filename);
        try {
            Stream<String> lines = Files.lines(file);
            retData = lines.collect(Collectors.toList());
        }catch(Throwable e) {
            throw new StorageException(e.getMessage(), e.getCause());
        }
        return retData;
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(this.rootLocation.toFile());
    }

}
