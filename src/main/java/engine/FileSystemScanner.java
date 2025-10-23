package engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Klasa odpowiedzialna za skanowanie systemu plików w poszukiwaniu dokumentów
 */
public class FileSystemScanner {

    /**
     * Metoda zwracająca wszystkie absolutne ścieżki do plików .txt z wybranego katalogu
     * @param path ścieżka do wybranego katalogu
     * @return pusty Set lub zawierający absolutne ścieżki do plików, o ile jakieś znajdowały się w katalogu
     * @throws FileNotFoundException rzucany jest, gdy wprowadzona ścieżka do katalogu jest błędna
     */
    public static Set<String> getAllFilesFromPath(String path) throws FileNotFoundException {
        File f = new File(path);
        if(!f.exists()){
            throw new FileNotFoundException("Plik lub katalog o podanej nazwie nie istnieje!");
        }
        if(!f.isDirectory()){
            throw new FileNotFoundException("Nie znaleziono katalogu o podanej nazwie!");
        }

        return Stream.of(Objects.requireNonNull(f.listFiles()))
                .filter(file -> !file.isDirectory() &&
                        file.getName().endsWith(".txt"))
                .map(File::getAbsolutePath)
                .collect(Collectors.toSet());
    }
}
