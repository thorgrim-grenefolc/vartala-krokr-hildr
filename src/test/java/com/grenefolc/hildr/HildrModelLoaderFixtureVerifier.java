package com.grenefolc.hildr;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class HildrModelLoaderFixtureVerifier {

    private HildrModelLoaderFixtureVerifier() {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 2) {
            System.err.println("Usage: HildrModelLoaderFixtureVerifier <jeraSeidr.xml> <boomiProfile.xml>");
            System.exit(2);
        }

        HildrModel jeraSeidr = load(args[0]);
        HildrModel boomiProfile = load(args[1]);
        assertEquivalent(jeraSeidr, boomiProfile);

        System.out.println("Hildr models equivalent: nodes=" + countNodes(jeraSeidr));
    }

    private static HildrModel load(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return HildrModelLoader.load(
                new String(bytes, StandardCharsets.UTF_8),
                new HildrExecutor.LogPackage()
        );
    }

    private static void assertEquivalent(HildrModel expected, HildrModel actual) {
        if (!safe(expected.getRootName()).equals(safe(actual.getRootName()))) {
            fail("rootName", expected.getRootName(), actual.getRootName());
        }
        if (expected.getTopLevelNodes().size() != actual.getTopLevelNodes().size()) {
            fail("topLevelNodes.size",
                    String.valueOf(expected.getTopLevelNodes().size()),
                    String.valueOf(actual.getTopLevelNodes().size()));
        }
        for (int i = 0; i < expected.getTopLevelNodes().size(); i++) {
            assertEquivalent(expected.getTopLevelNodes().get(i), actual.getTopLevelNodes().get(i), "/" + i);
        }
    }

    private static void assertEquivalent(HildrModelNode expected, HildrModelNode actual, String path) {
        compare(path, "nodeType", expected.getNodeType(), actual.getNodeType());
        compare(path, "id", expected.getId(), actual.getId());
        compare(path, "description", expected.getDescription(), actual.getDescription());
        compare(path, "occurs", expected.getOccurs(), actual.getOccurs());
        compare(path, "length", expected.getLength(), actual.getLength());
        compare(path, "recognitionCode", expected.getRecognitionCode(), actual.getRecognitionCode());

        if (expected.getChildren().size() != actual.getChildren().size()) {
            fail(path + ".children.size",
                    String.valueOf(expected.getChildren().size()),
                    String.valueOf(actual.getChildren().size()));
        }
        for (int i = 0; i < expected.getChildren().size(); i++) {
            assertEquivalent(expected.getChildren().get(i), actual.getChildren().get(i), path + "/" + i);
        }
    }

    private static void compare(String path, String field, String expected, String actual) {
        if (!safe(expected).equals(safe(actual))) {
            fail(path + "." + field, expected, actual);
        }
    }

    private static int countNodes(HildrModel model) {
        int total = 0;
        for (HildrModelNode node : model.getTopLevelNodes()) {
            total += countNodes(node);
        }
        return total;
    }

    private static int countNodes(HildrModelNode node) {
        int total = 1;
        for (HildrModelNode child : node.getChildren()) {
            total += countNodes(child);
        }
        return total;
    }

    private static void fail(String path, String expected, String actual) {
        throw new IllegalStateException("Model mismatch at " + path
                + ": expected=[" + safe(expected) + "], actual=[" + safe(actual) + "]");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
