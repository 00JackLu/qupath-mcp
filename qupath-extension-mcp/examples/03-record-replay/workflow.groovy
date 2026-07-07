// QuPath-MCP recorded workflow
// Saved by qupath_save_workflow — regenerable and re-runnable without an LLM.
// Headless replay:  qupath script workflow.groovy -p project.qpproj

setImageType('FLUORESCENCE')

// Full-image annotation covering the whole slide
createSelectAllObject(true)

// DAPI Watershed cell detection (typed parameters captured at record time)
runPlugin(
    'qupath.imagej.detect.cells.WatershedCellDetection',
    '{"detectionImageFluorescence":1,"requestedPixelSizeMicrons":0.5,' +
    '"backgroundRadiusMicrons":8.0,"medianRadiusMicrons":0.0,' +
    '"sigmaMicrons":1.5,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,' +
    '"threshold":1.0,"watershedPostProcess":true,"cellExpansionMicrons":5.0,' +
    '"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}'
)
