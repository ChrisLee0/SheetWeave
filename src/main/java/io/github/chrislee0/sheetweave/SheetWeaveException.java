package io.github.chrislee0.sheetweave;

/** Invalid template, expression or stylesheet. */
public final class SheetWeaveException extends IllegalArgumentException {
    public SheetWeaveException(String message) { super(message); }
    public SheetWeaveException(String message, Throwable cause) { super(message, cause); }
}
