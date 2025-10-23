package engine;

import java.io.File;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Klasa odpowiedzialna za skanowanie systemu plików w poszukiwaniu dokumentów
 */
public class FileSystemScanner {

    public void readPath(){
        System.out.print("Proszę podać nazwę partycji lub ścieżkę do wybranego katalogu: ");
        Scanner sc = new Scanner(System.in);
        String name = sc.nextLine();
        File f = new File(name);
        if(!f.exists()){
            System.out.println("Plik lub katalog o podanej nazwie nie istnieje!");
            return;
        }
        if(!f.isDirectory()){
            System.out.println("Nie znaleziono katalogu o podanej nazwie!");
            return;
        }
        System.out.println("OK");
        Set<String> files = Stream.of(Objects.requireNonNull(f.listFiles()))
                .filter(file -> !file.isDirectory() &&
                        file.getName().endsWith(".pdf"))
                .map(File::getName)
                .collect(Collectors.toSet());

        for(String fileName : files){
            System.out.println(fileName);
        }
    }
}
