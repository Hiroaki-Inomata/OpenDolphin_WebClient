package open.dolphin.shared.text;

public class KanjiHelper {

    private static final char FIRST_HIRAGANA = 'ぁ';
    private static final char LAST_HIRAGANA = 'ん';
    private static final char FIRST_KATAKANA = 'ァ';
    private static final char LAST_KATAKANA = 'ヶ';

    private static final String[] HALF_KATAKANA = {"ｧ", "ｱ", "ｨ", "ｲ", "ｩ",
        "ｳ", "ｪ", "ｴ", "ｫ", "ｵ", "ｶ", "ｶﾞ", "ｷ", "ｷﾞ", "ｸ", "ｸﾞ", "ｹ",
        "ｹﾞ", "ｺ", "ｺﾞ", "ｻ", "ｻﾞ", "ｼ", "ｼﾞ", "ｽ", "ｽﾞ", "ｾ", "ｾﾞ", "ｿ",
        "ｿﾞ", "ﾀ", "ﾀﾞ", "ﾁ", "ﾁﾞ", "ｯ", "ﾂ", "ﾂﾞ", "ﾃ", "ﾃﾞ", "ﾄ", "ﾄﾞ",
        "ﾅ", "ﾆ", "ﾇ", "ﾈ", "ﾉ", "ﾊ", "ﾊﾞ", "ﾊﾟ", "ﾋ", "ﾋﾞ", "ﾋﾟ", "ﾌ",
        "ﾌﾞ", "ﾌﾟ", "ﾍ", "ﾍﾞ", "ﾍﾟ", "ﾎ", "ﾎﾞ", "ﾎﾟ", "ﾏ", "ﾐ", "ﾑ", "ﾒ",
        "ﾓ", "ｬ", "ﾔ", "ｭ", "ﾕ", "ｮ", "ﾖ", "ﾗ", "ﾘ", "ﾙ", "ﾚ", "ﾛ", "ﾜ",
        "ﾜ", "ｲ", "ｴ", "ｦ", "ﾝ", "ｳﾞ", "ｶ", "ｹ"};

    public static boolean isKatakana(char c) {
        return c >= FIRST_KATAKANA && c <= LAST_KATAKANA;
    }

    public static boolean isHiragana(char c) {
        return c >= FIRST_HIRAGANA && c <= LAST_HIRAGANA;
    }

    private static char toKatakana(char c) {
        return (char) ((int) FIRST_KATAKANA + (int) c - (int) FIRST_HIRAGANA);
    }

    public static String hiraganaToKatakana(String s) {
        int len = s.length();
        char[] src = new char[len];
        s.getChars(0, s.length(), src, 0);

        char[] dst = new char[len];
        for (int i = 0; i < len; i++) {
            if (isHiragana(src[i])) {
                dst[i] = toKatakana(src[i]);
            } else {
                dst[i] = src[i];
            }
        }
        return new String(dst);
    }

    public static String matomoTobaka(String s) {
        int len = s.length();
        char[] src = new char[len];
        s.getChars(0, s.length(), src, 0);

        char[] dst = new char[len];
        for (int i = 0; i < len; i++) {
            int index = (int) src[i] - (int) FIRST_KATAKANA;
            dst[i] = HALF_KATAKANA[index].charAt(0);
        }
        return new String(dst);
    }
}
