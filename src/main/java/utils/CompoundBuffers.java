package utils;

import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;

/// Just want to test if freeing a PointerBuffer automatically frees the memory spaces those pointers
/// are referencing
public class CompoundBuffers {

    public PointerBuffer pointerBuffer;
    public ByteBuffer aStringRepresentation;
    public ByteBuffer anotherStringRepresentation;

    public CompoundBuffers(PointerBuffer pointerBuffer, ByteBuffer aStringRepresentation, ByteBuffer anotherStringRepresentation) {
        this.pointerBuffer = pointerBuffer;
        this.aStringRepresentation = aStringRepresentation;
        this.anotherStringRepresentation = anotherStringRepresentation;
    }
}
