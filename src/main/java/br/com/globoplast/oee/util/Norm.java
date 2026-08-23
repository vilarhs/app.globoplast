package br.com.globoplast.oee.util;

import br.com.globoplast.oee.config.AppConfig;

import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class Norm {
    private Norm() {}

    public static String text(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("nan") || s.equalsIgnoreCase("none")) return "";
        return s;
    }

    public static String username(Object value) { return text(value).toUpperCase(Locale.ROOT); }

    public static String token(Object value) {
        String s = text(value);
        if (s.endsWith(".0")) {
            String p = s.substring(0, s.length()-2);
            if (p.matches("-?\\d+")) s = p;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    public static String product(Object value) { return token(value).replace(" ", ""); }
    public static String order(Object value) { return token(value); }

    public static String fold(Object value) {
        String s = Normalizer.normalize(text(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return s;
    }

    public static double dbl(Object value, double def) {
        if (value == null) return def;
        try {
            String s = text(value);
            if (s.isBlank()) return def;
            if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(',', '.');
            else if (s.contains(",")) s = s.replace(',', '.');
            return Double.parseDouble(s);
        } catch (Exception ex) {
            try { return Double.parseDouble(text(value).replace(',', '.')); } catch (Exception ignored) { return def; }
        }
    }

    public static int integer(Object value, int def) {
        if (value == null) return def;
        try { return (int)Math.round(Double.parseDouble(text(value).replace(',', '.'))); }
        catch (Exception ex) { return def; }
    }

    public static LocalDate isoDate(Object value) {
        String s = text(value);
        if (s.isBlank()) return null;
        try { return LocalDate.parse(s.substring(0, Math.min(10, s.length()))); }
        catch (Exception ignored) {}
        for (DateTimeFormatter f : new DateTimeFormatter[]{DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("yyyy/MM/dd")}) {
            try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    public static LocalDate productiveDate(LocalDate rawDate, String shift) {
        if (rawDate == null) return null;
        return "C".equalsIgnoreCase(text(shift)) ? rawDate.minusDays(1) : rawDate;
    }

    /**
     * Refugo não traz hora de lançamento no payload do ERP. Para A/B, usa a
     * primeira detecção somente quando ela pertence à própria DATA_APON; antes
     * das 06h o registro ainda pertence ao dia produtivo anterior. O Turno C
     * conserva a regra operacional histórica e sempre retrocede um dia.
     */
    public static LocalDate productiveScrapDate(LocalDate rawDate, String shift, Object firstDetectedAt) {
        LocalDate legacy = productiveDate(rawDate, shift);
        if (rawDate == null || "C".equalsIgnoreCase(text(shift))) return legacy;

        String value = text(firstDetectedAt);
        if (value.isBlank()) return legacy;
        try {
            ZonedDateTime detected = ZonedDateTime.parse(value).withZoneSameInstant(AppConfig.ZONE);
            if (detected.toLocalDate().equals(rawDate) && detected.getHour() < 6) return rawDate.minusDays(1);
        } catch (Exception ignored) {
            try {
                OffsetDateTime detected = OffsetDateTime.parse(value);
                ZonedDateTime local = detected.atZoneSameInstant(AppConfig.ZONE);
                if (local.toLocalDate().equals(rawDate) && local.getHour() < 6) return rawDate.minusDays(1);
            } catch (Exception ignoredAgain) { }
        }
        return legacy;
    }

    public static LocalDate productiveToday() {
        ZonedDateTime now = ZonedDateTime.now(AppConfig.ZONE);
        if (now.getHour() < 6) now = now.minusDays(1);
        return now.toLocalDate();
    }

    public static String br(LocalDate d) { return d == null ? "" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    public static String syncTime(Object value) {
        String s = text(value);
        if (s.isBlank()) return "";
        try {
            OffsetDateTime dt = OffsetDateTime.parse(s);
            return dt.atZoneSameInstant(AppConfig.ZONE).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception ignored) {
            if (s.length() >= 19 && s.charAt(10) == 'T') return s.substring(11,19);
            return "";
        }
    }

    /**
     * Nome canônico usado pelos apontamentos ERP.
     *
     * Normaliza aliases históricos de COL TPA/EXTRUSÃO/FOR DE OMBRO/DECORAÇÃO.
     * Na base real foi identificado também o par operacional INJEÇÃO -> INJETORA
     * e FECHA HOT AIR -> HOT AIR. Esses dois aliases são necessários para que o
     * catálogo real (maquinas) seja encontrado sem alterar o valor bruto do ERP.
     */
    public static String machine(String value) {
        String name = text(value);
        String u = name.toUpperCase(Locale.ROOT);
        String[][] aliases = {
                {"COL TPA ", "COL DE TAMPA "},
                {"EXTRUSÃO ", "EXTRUSORA "}, {"EXTRUSAO ", "EXTRUSORA "},
                {"FOR DE OMBRO ", "FORMADORA "},
                {"DECORAÇÂO ", "IMPRESSORA "}, {"DECORAÇÃO ", "IMPRESSORA "}, {"DECORACAO ", "IMPRESSORA "},
                {"INJEÇÂO ", "INJETORA "}, {"INJEÇÃO ", "INJETORA "}, {"INJECAO ", "INJETORA "},
                {"FECHA HOT AIR ", "HOT AIR "}
        };
        for (String[] a : aliases) {
            if (u.startsWith(a[0])) return a[1] + name.substring(a[0].length()).trim();
        }
        return name;
    }

    /**
     * Chave estável de equivalência de máquina. Remove acentos/espaços e
     * normaliza zeros à esquerda do número, mas nunca aproxima famílias
     * diferentes. Ex.: EXTRUSÃO 03 == EXTRUSORA 3; INJEÇÂO 05 == INJETORA 05.
     */
    public static String machineKey(Object value) {
        String normalized = machine(text(value));
        String folded = fold(normalized).replaceAll("[^a-z0-9]+", " ").trim();
        if (folded.isBlank()) return "";
        StringBuilder key = new StringBuilder();
        for (String part : folded.split("\\s+")) {
            if (part.matches("\\d+")) {
                try { key.append(Integer.parseInt(part)); }
                catch (Exception ignored) { key.append(part); }
            } else {
                key.append(part);
            }
        }
        return key.toString();
    }

    public static String sectorFromMachineRaw(String raw) {
        String u = text(raw).toUpperCase(Locale.ROOT);
        if (u.startsWith("COL TPA ")) return "COL DE TAMPA";
        if (u.startsWith("EXTRUSÃO ") || u.startsWith("EXTRUSAO ") || u.startsWith("FOR DE OMBRO ") || u.startsWith("LUVA COEX ")) return "EXTRUSÃO";
        if (u.startsWith("DECORAÇÂO ") || u.startsWith("DECORAÇÃO ") || u.startsWith("DECORACAO ")) return "IMPRESSÃO";
        if (u.startsWith("FECHA HOT AIR ")) return "FECHAMENTO";
        if (u.startsWith("HOT ")) return "HOT STAMPING";
        if (u.startsWith("INJEÇÂO ") || u.startsWith("INJEÇÃO ") || u.startsWith("INJECAO ")) return "INJETADOS";
        if (u.startsWith("SILK ")) return "SERIGRAFIA";
        return "Sem Setor";
    }

    public static String scrapOriginCode(String product) {
        String code = product(product);
        if (code.startsWith("777")) return code;
        return code.length() >= 3 ? code.substring(0, 3) : code;
    }

    public static String scrapMotive(String product, String motive) {
        String code = product(product);
        if (code.equals("777028") || code.equals("777029")) return "BORRA";
        String value = text(motive);
        return value.isBlank() ? "NÃO INFORMADO" : value;
    }

    public static String scrapMotiveUid(String product, String motive) {
        return scrapOriginCode(product) + "¦" + scrapSector(product) + "¦" + scrapMotive(product, motive);
    }

    /** Mesma classificação de setor usada pelo Refugo do app Python original. */
    public static String scrapSector(String product) {
        String code = product(product);
        return switch (code) {
            case "777021" -> "Qualidade";
            case "777020" -> "Desenvolvimento";
            case "777028" -> "Extrusão";
            case "777024" -> "Preparação MP";
            case "777023" -> "Varredura Armazém";
            case "777025" -> "Devolução Cliente";
            case "777027" -> "Material Obsoleto";
            case "777029" -> "Injetados";
            case "777022" -> "Varredura Fábrica";
            default -> switch (code.length() >= 3 ? code.substring(0,3) : code) {
                case "770" -> "Extrusão";
                case "771" -> "Impressão";
                case "772" -> "Silk Screen";
                case "773" -> "Hot Stamping";
                case "775" -> "Fechamento de Fundo";
                case "776" -> "Colocação de Tampa";
                case "994", "993", "120" -> "Injetados";
                default -> "Injetados";
            };
        };
    }

    public static String canonicalSector(Object value) {
        String s = token(value);
        return switch (s) {
            case "EXTRUSORA", "EXTRUSÃO", "EXTRUSAO" -> "EXTRUSÃO";
            case "IMPRESSÃO", "IMPRESSAO", "DECORAÇÃO", "DECORACAO" -> "IMPRESSÃO";
            case "SILK SCREEN", "SERIGRAFIA" -> "SERIGRAFIA";
            case "HOT STAMPING" -> "HOT STAMPING";
            case "FECHAMENTO DE FUNDO", "FECHAMENTO" -> "FECHAMENTO";
            case "COLOCAÇÃO DE TAMPA", "COLOCACAO DE TAMPA", "COL DE TAMPA" -> "COL DE TAMPA";
            case "INJETADOS", "INJETADO" -> "INJETADOS";
            default -> s;
        };
    }

    public static double round(double x, int places) {
        double p = Math.pow(10, places);
        return Math.round(x * p) / p;
    }
}
