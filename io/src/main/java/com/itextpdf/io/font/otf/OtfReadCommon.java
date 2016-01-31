package com.itextpdf.io.font.otf;

import com.itextpdf.io.source.RandomAccessFileOrArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OtfReadCommon {
    public static int[] readUShortArray(RandomAccessFileOrArray rf, int size, int location) throws java.io.IOException {
        int[] ret = new int[size];
        for (int k = 0; k < size; ++k) {
            int offset = rf.readUnsignedShort();
            ret[k] = offset == 0 ? offset : offset + location;
        }
        return ret;
    }
    
    public static int[] readUShortArray(RandomAccessFileOrArray rf, int size) throws java.io.IOException {
        return readUShortArray(rf, size, 0);
    }

    public static void readCoverages(RandomAccessFileOrArray rf, int[] locations, List<Set<Integer>> coverage) throws java.io.IOException {
        for (int location : locations) {
            coverage.add(new HashSet<>(readCoverageFormat(rf, location)));
        }
    }

	public static List<Integer> readCoverageFormat(RandomAccessFileOrArray rf, int coverageLocation)
			throws java.io.IOException {
		rf.seek(coverageLocation);
		int coverageFormat = rf.readShort();
		List<Integer> glyphIds;
		if (coverageFormat == 1) {
			int glyphCount = rf.readShort();
			glyphIds = new ArrayList<>(glyphCount);
			for (int i = 0; i < glyphCount; i++) {
				int coverageGlyphId = rf.readShort();
				glyphIds.add(coverageGlyphId);
			}
		} else if (coverageFormat == 2) {
			int rangeCount = rf.readShort();
			glyphIds = new ArrayList<>();
			for (int i = 0; i < rangeCount; i++) {
				readRangeRecord(rf, glyphIds);
			}

		} else {
			throw new UnsupportedOperationException(String.format("Invalid coverage format: %d", coverageFormat));
		}

		return Collections.unmodifiableList(glyphIds);
	}

	private static void readRangeRecord(RandomAccessFileOrArray rf, List<Integer> glyphIds) throws java.io.IOException {
		int startGlyphId = rf.readShort();
		int endGlyphId = rf.readShort();
		@SuppressWarnings("unused")
        int startCoverageIndex = rf.readShort();
		for (int glyphId = startGlyphId; glyphId <= endGlyphId; glyphId++) {
			glyphIds.add(glyphId);
		}
	}

    public static GposValueRecord readGposValueRecord(OpenTypeFontTableReader tableReader, int mask) throws java.io.IOException {
        GposValueRecord vr = new GposValueRecord();
        if ((mask & 0x0001) != 0) {
            vr.XPlacement = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
        }
        if ((mask & 0x0002) != 0) {
            vr.YPlacement = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
        }
        if ((mask & 0x0004) != 0) {
            vr.XAdvance = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
        }
        if ((mask & 0x0008) != 0) {
            vr.YAdvance = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
        }
        if ((mask & 0x0010) != 0) {
            tableReader.rf.skip(2);
        }
        if ((mask & 0x0020) != 0) {
            tableReader.rf.skip(2);
        }
        if ((mask & 0x0040) != 0) {
            tableReader.rf.skip(2);
        }
        if ((mask & 0x0080) != 0) {
            tableReader.rf.skip(2);
        }
        return vr;
    }
    
    public static GposAnchor readGposAnchor(OpenTypeFontTableReader tableReader, int location) throws java.io.IOException {
        if (location == 0) {
            return null;
        }
        tableReader.rf.seek(location);
        int format = tableReader.rf.readUnsignedShort();
        GposAnchor t = null;

        switch (format) {
            default:
                t = new GposAnchor();
                t.XCoordinate = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
                t.YCoordinate = tableReader.rf.readShort() * 1000 / tableReader.getUnitsPerEm();
                break;
        }

        return t;
    }
    
    public static List<OtfMarkRecord> readMarkArray(OpenTypeFontTableReader tableReader, int location) throws java.io.IOException {
        tableReader.rf.seek(location);
        int markCount = tableReader.rf.readUnsignedShort();
        int[] classes = new int[markCount];
        int[] locations = new int[markCount];
        for (int k = 0; k < markCount; ++k) {
            classes[k] = tableReader.rf.readUnsignedShort();
            int offset = tableReader.rf.readUnsignedShort();
            locations[k] = location + offset;
        }
        List<OtfMarkRecord> marks = new ArrayList<OtfMarkRecord>();
        for (int k = 0; k < markCount; ++k) {
            OtfMarkRecord rec = new OtfMarkRecord();
            rec.markClass = classes[k];
            rec.anchor = readGposAnchor(tableReader, locations[k]);
            marks.add(rec);
        }
        return marks;
    }
    
    public static SubstLookupRecord[] readSubstLookupRecords(RandomAccessFileOrArray rf, int substCount) throws java.io.IOException {
        SubstLookupRecord[] substPosLookUpRecords = new SubstLookupRecord[substCount];
        for (int i = 0; i < substCount; ++i) {
            SubstLookupRecord slr = new SubstLookupRecord();
            slr.sequenceIndex = rf.readUnsignedShort();
            slr.lookupListIndex = rf.readUnsignedShort();
            substPosLookUpRecords[i] = slr;
        }
        return substPosLookUpRecords;
    }

    public static GposAnchor[] readAnchorArray(OpenTypeFontTableReader tableReader, int[] locations, int left, int right) throws java.io.IOException {
        GposAnchor[] anchors = new GposAnchor[right - left];
        for (int i = left; i < right; i++) {
            anchors[i - left] = readGposAnchor(tableReader, locations[i]);
        }
        return anchors;
    }

    public static List<GposAnchor[]> readBaseArray(OpenTypeFontTableReader tableReader, int classCount, int location) throws java.io.IOException {
        List<GposAnchor[]> baseArray = new ArrayList<>();
        tableReader.rf.seek(location);
        int baseCount = tableReader.rf.readUnsignedShort();
        int[] anchorLocations = readUShortArray(tableReader.rf, baseCount * classCount, location);
        int idx = 0;
        for (int k = 0; k < baseCount; ++k) {
            baseArray.add(readAnchorArray(tableReader, anchorLocations, idx, idx + classCount));
            idx += classCount;
        }
        return baseArray;
    }

    public static List<List<GposAnchor[]>> readLigatureArray(OpenTypeFontTableReader tableReader, int classCount, int location) throws java.io.IOException {
        List<List<GposAnchor[]>> ligatureArray = new ArrayList<>();
        tableReader.rf.seek(location);
        int ligatureCount = tableReader.rf.readUnsignedShort();
        int[] ligatureAttachLocations = readUShortArray(tableReader.rf, ligatureCount, location);
        for (int liga = 0; liga < ligatureCount; ++liga) {
            int ligatureAttachLocation = ligatureAttachLocations[liga];
            List<GposAnchor[]> ligatureAttach = new ArrayList<>();
            tableReader.rf.seek(ligatureAttachLocation);
            int componentCount = tableReader.rf.readUnsignedShort();
            int[] componentRecordsLocation = readUShortArray(tableReader.rf, classCount * componentCount, ligatureAttachLocation);
            int idx = 0;
            for (int k = 0; k < componentCount; ++k) {
                ligatureAttach.add(readAnchorArray(tableReader, componentRecordsLocation, idx, idx + classCount));
                idx += classCount;
            }
            ligatureArray.add(ligatureAttach);
        }
        return ligatureArray;
    }
}