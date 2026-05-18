package com.grenefolc.hildr;

public final class HildrPresentationHandlerFactory {

    private HildrPresentationHandlerFactory() {
    }

    public static HildrPresentationHandler resolve(String presentationClass) {
        if ("ffv".equalsIgnoreCase(presentationClass)) {
            return new HildrFfvPresentationHandler();
        }
        return new HildrCsvPresentationHandler();
    }
}
