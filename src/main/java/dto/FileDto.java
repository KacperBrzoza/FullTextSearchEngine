package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

/**
 * Klasa przechowująca metadane dokumentu
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileDto implements Serializable {

    /**
     * Unikalne ID
     */
    private UUID id;

    /**
     * Ścieżka do pliku
     */
    private String path;

    /**
     * Słownik częstości tokenów występujących w dokumencie
     */
    private Map<String, Double> frequencyDict;
}
