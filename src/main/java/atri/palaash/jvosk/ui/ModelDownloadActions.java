package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.VoskModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Model Manager action labels and eligibility. Kept out of the dialog so
 * update/reinstall/resume behaviour can be tested without showing a window.
 */
public final class ModelDownloadActions {
    private ModelDownloadActions() {}

    public static boolean isDownloadCandidate(VoskModel model,
                                              Set<String> updatesAvailable,
                                              Set<String> metadataMissing) {
        return hasDownloadSource(model)
                && (!model.isInstalled()
                || updatesAvailable.contains(model.getName())
                || metadataMissing.contains(model.getName()));
    }

    /**
     * Whether a row has a real catalogue archive to download. Locally installed
     * models that disappeared from the catalogue use an {@code installed://}
     * placeholder and must not offer a reinstall action.
     */
    public static boolean hasDownloadSource(VoskModel model) {
        String url = model.getDownloadUrl();
        return url != null
                && (url.startsWith("https://") || url.startsWith("http://"));
    }

    public static String downloadButtonText(VoskModel model,
                                            Set<String> updatesAvailable,
                                            Set<String> metadataMissing,
                                            boolean partial) {
        if (model.isInstalled() && updatesAvailable.contains(model.getName())) {
            return "Update Model";
        }
        if (model.isInstalled() && metadataMissing.contains(model.getName())) {
            return "Reinstall Model";
        }
        if (partial) {
            return "Resume Download";
        }
        return "Download Model";
    }

    /**
     * Resolve persisted queue names against the current catalogue, dropping
     * unknown, duplicate, and already-installed names that are not update or
     * reinstall candidates.
     */
    public static List<VoskModel> resumableModels(List<String> pending,
                                                  List<VoskModel> catalogue,
                                                  Set<String> updatesAvailable,
                                                  Set<String> metadataMissing) {
        Map<String, VoskModel> byName = new java.util.LinkedHashMap<>();
        for (VoskModel model : catalogue) {
            byName.put(model.getName(), model);
        }
        return pending.stream()
                .map(byName::get)
                .filter(java.util.Objects::nonNull)
                .filter(model -> isDownloadCandidate(model, updatesAvailable, metadataMissing))
                .distinct()
                .toList();
    }
}
