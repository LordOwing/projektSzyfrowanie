import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    static char[][] table = new char[5][5];
    static int[] rzad = new int[26];
    static int[] kolumna = new int[26];

    public static void main(String[] args) throws Exception {
        Scanner skaner = new Scanner(System.in);

        System.out.print("Podaj ściezke pliku wejsciowego: ");
        String sciezkaWej = skaner.nextLine();

        System.out.print("Podaj ścieżkę pliku wyjsciowego: ");
        String sciezkaWyj = skaner.nextLine();

        System.out.print("Podaj klucz: ");
        String haslo = skaner.nextLine();

        System.out.print("Wybierz operacje (szyfruj/odszyfruj): ");
        String wybor = skaner.nextLine().trim().toLowerCase();

        System.out.print("Podaj liczbe watkow: ");
        int podana = Integer.parseInt(skaner.nextLine());
        if (podana <= 0) {
            System.out.println("Liczba watkow musi byc dodatnia. Uzyje 1.");
        }
        int iloscW = Math.max(1, podana);
        buildTable(haslo);

        String zawartosc = readFile(sciezkaWej);
        List<String> powtorzone = wybor.startsWith("o") ? pairsForDecryption(zawartosc) : pairsForEncryption(zawartosc);
        String wynik = processInThreads(powtorzone, iloscW, wybor.startsWith("o"));

        writeFile(sciezkaWyj, wynik);
        System.out.println("Gotowe.");
    }

    static String readFile(String sciezka) throws IOException {
        return new String(Files.readAllBytes(Paths.get(sciezka)), StandardCharsets.UTF_8);
    }

    static void writeFile(String sciezka, String tresc) throws IOException {
        Files.write(Paths.get(sciezka), tresc.getBytes(StandardCharsets.UTF_8));
    }

    static void buildTable(String haslo) {
        boolean[] zajete = new boolean[26];
        String ciag = normalize(haslo) + "ABCDEFGHIKLMNOPQRSTUVWXYZ";
        int miejsce = 0;

        for (int i = 0; i < ciag.length(); i++) {
            char znak = ciag.charAt(i);
            int numerLit = znak - 'A';

            if (!zajete[numerLit]) {
                zajete[numerLit] = true;
                table[miejsce / 5][miejsce % 5] = znak;
                rzad[numerLit] = miejsce / 5;
                kolumna[numerLit] = miejsce % 5;
                miejsce++;
            }
        }
    }
    

    static String normalize(String tekst) {
        StringBuilder normalized = new StringBuilder();

        for (int i = 0; i < tekst.length(); i++) {
            char znak = replacePolish(Character.toUpperCase(tekst.charAt(i)));

            if (znak >= 'A' && znak <= 'Z') {
                normalized.append(znak == 'J' ? 'I' : znak);
            }
        }

        return normalized.toString();
    }

    static char replacePolish(char znak) {
        switch (znak) {
            case 'Ą':
                return 'A';
            case 'Ć':
                return 'C';
            case 'Ę':
                return 'E';
            case 'Ł':
                return 'L';
            case 'Ń':
                return 'N';
            case 'Ó':
                return 'O';
            case 'Ś':
                return 'S';
            case 'Ź':
            case 'Ż':
                return 'Z';
            default:
                return znak;
        }
    }

    static List<String> pairsForEncryption(String tekst) {
        String znormalizowany = normalize(tekst);
        List<String> powtorzone = new ArrayList<>();
        int i = 0;

        while (i < znormalizowany.length()) {
            char pierwszy = znormalizowany.charAt(i);
            char drugi;

            if (i + 1 >= znormalizowany.length()) {
                drugi = 'X';
                i++;
            } else if (pierwszy == znormalizowany.charAt(i + 1)) {
                drugi = 'X';
                i++;
            } else {
                drugi = znormalizowany.charAt(i + 1);
                i += 2;
            }

            powtorzone.add("" + pierwszy + drugi);
        }

        return powtorzone;
    }

    static List<String> pairsForDecryption(String tekst) {
        String znormalizowany = normalize(tekst);
        List<String> powtorzone = new ArrayList<>();

        if (znormalizowany.length() % 2 == 1) {
            znormalizowany += "X";
        }

        for (int i = 0; i < znormalizowany.length(); i += 2) {
            powtorzone.add(znormalizowany.substring(i, i + 2));
        }

        return powtorzone;
    }

    static String processPart(List<String> powtorzone, boolean odszyfruj) {
        StringBuilder wynik = new StringBuilder();

        for (String dwuznak : powtorzone) {
            wynik.append(processPair(dwuznak.charAt(0), dwuznak.charAt(1), odszyfruj));
        }

        return wynik.toString();
    }
    static String processInThreads(List<String> powtorzone, int iloscW, boolean odszyfruj) throws Exception {
        iloscW = Math.min(iloscW, Math.max(1, powtorzone.size()));
        ExecutorService executor = Executors.newFixedThreadPool(iloscW);
        try {
            List<Future<String>> pula = new ArrayList<>();
            int rozmiar = (powtorzone.size() + iloscW - 1) / iloscW;

            for (int i = 0; i < powtorzone.size(); i += rozmiar) {
                int odKad = i;
                int doKad = Math.min(i + rozmiar, powtorzone.size());
                Callable<String> zadanie = () -> processPart(powtorzone.subList(odKad, doKad), odszyfruj);
                pula.add(executor.submit(zadanie));
            }

            StringBuilder result = new StringBuilder();
            for (Future<String> x : pula) {
                result.append(x.get());
            }
            return result.toString();
        } finally {
            executor.shutdown();
        }
    }


    static String processPair(char lewa, char prawa, boolean odszyfruj) {
        int rowA = rzad[lewa - 'A'];
        int kolA = kolumna[lewa - 'A'];
        
        int rowB = rzad[prawa - 'A'];
        int kolB = kolumna[prawa - 'A'];
        int przesuniecie = odszyfruj ? 4 : 1;

        if (rowA == rowB) {
            return "" + table[rowA][(kolA + przesuniecie) % 5] + table[rowB][(kolB + przesuniecie) % 5];
        }
        

        if (kolA == kolB) {
            return "" + table[(rowA + przesuniecie) % 5][kolA] + table[(rowB + przesuniecie) % 5][kolB];
        }

        return "" + table[rowA][kolB] + table[rowB][kolA];
    }
    
}
