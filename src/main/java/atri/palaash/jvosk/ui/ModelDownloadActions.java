package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.VoskModel;

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
        return !model.isInstalled()
                || updatesAvailable.contains(model.getName())
                || metadataMissing.contains(model.getName());
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
}
