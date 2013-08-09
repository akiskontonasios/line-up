package lineup.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class Fun {
    public static <T> List<T> take(int length, List<T> list) {
        if (length < list.size()) {
            return list.subList(0, length);
        } else {
            return list;
        }
    }

    public static <T> List<T> drop(int length, List<T> list) {
        if (length <= list.size()) {
            return list.subList(length, list.size());
        } else {
            return new LinkedList<T>();
        }
    }

    public static <T> T head(List<T> list) {
        return list.get(0);
    }

    public static <T> T last(List<T> list) {
        if (list.size() == 0) {
            return null;
        } else {
            return list.get(list.size() - 1);
        }
    }

    public static <T> List<T> List(T... values) {
        return Arrays.asList(values);
    }

    public static <A, B> Tuple<A, B> tuple(A a, B b) {
        return new Tuple<A, B>(a, b);
    }

    public static String mkString(Collection<?> items, String prefix, String separator, String postfix) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (prefix != null)
            sb.append(prefix);

        for (Object item : items) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }

            sb.append(item.toString());
        }

        if (postfix != null)
            sb.append(postfix);

        return sb.toString();
    }

    public static String mkString(Collection<?> items, String separator) {
        return mkString(items, null, separator, null);
    }

    public static String cut(String str, int length, String cont) {
        StringBuilder sb = new StringBuilder();
        if (str.length() > length) {
            sb.append(str.substring(0, length));
            if (cont != null) {
                sb.append(cont);
            }
        } else {
            sb.append(str);
        }

        return sb.toString();
    }

    public static String cut(String str, int length) {
        return cut(str, length, null);
    }
}
