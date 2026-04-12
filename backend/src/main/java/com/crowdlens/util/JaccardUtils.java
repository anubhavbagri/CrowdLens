package com.crowdlens.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JaccardUtils {

    /**
     * Calculates Jaccard similarity between two strings based on their word sets.
     */
    public static double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
        return calculateSimilarity(words1, words2);
    }

    /**
     * Calculates Jaccard similarity between two sets of strings.
     */
    public static double calculateSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }
}
