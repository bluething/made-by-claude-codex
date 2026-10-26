package io.github.bluething.searchindexer.cli;

import picocli.CommandLine.IVersionProvider;

/** Reads the version Maven writes into the jar manifest, so it is never duplicated in source. */
public final class ManifestVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = ManifestVersionProvider.class.getPackage().getImplementationVersion();
        return new String[] {"search-indexer " + (version == null ? "(development build)" : version)};
    }
}
