package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;

public class CCNBTUtils {

    public static NBTTagCompound loadTag(byte[] data) throws IOException {
        if (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));) {
                data = IOUtils.toByteArray(gzip);
            }
        }

        return CompressedStreamTools.func_152456_a(new DataInputStream(new ByteArrayInputStream(data)), NBTSizeTracker.field_152451_a);
    }

    public static byte[] saveTag(NBTTagCompound tag, boolean compress) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        CompressedStreamTools.write(tag, new DataOutputStream(baos));

        byte[] data = baos.toByteArray();

        if (compress) {
            ByteArrayOutputStream zipped = new ByteArrayOutputStream(data.length);

            try (GZIPOutputStream zipstream = new GZIPOutputStream(zipped)) {
                IOUtils.write(data, zipstream);
                zipstream.flush();
            }

            data = zipped.toByteArray();
        }

        return data;
    }
}
