package io.jenkins.plugins.analysis.core.model;

import java.io.IOException;
import java.util.Optional;

import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;

import io.jenkins.plugins.analysis.core.charts.SeverityTrendChart;
import io.jenkins.plugins.analysis.core.charts.ToolsTrendChart;
import io.jenkins.plugins.analysis.core.util.TrendChartType;
import io.jenkins.plugins.echarts.AsyncTrendChart;

/**
 * A job action displays a link on the side panel of a job. This action also is responsible to render the historical
 * trend via its associated 'floatingBox.jelly' view.
 *
 * @author Ullrich Hafner
 */
public class JobAction implements Action, AsyncTrendChart {
    private final Job<?, ?> owner;
    private final StaticAnalysisLabelProvider labelProvider;
    private final int numberOfTools;
    private final TrendChartType trendChartType;

    /**
     * Creates a new instance of {@link JobAction}.
     *
     * @param owner
     *         the job that owns this action
     * @param labelProvider
     *         the label provider
     * @deprecated use {@link #JobAction(Job, StaticAnalysisLabelProvider, int)}
     */
    @Deprecated
    public JobAction(final Job<?, ?> owner, final StaticAnalysisLabelProvider labelProvider) {
        this(owner, labelProvider, 1);
    }

    /**
     * Creates a new instance of {@link JobAction}.
     *
     * @param owner
     *         the job that owns this action
     * @param labelProvider
     *         the label provider
     * @param numberOfTools
     *         the number of tools that have results to show
     */
    public JobAction(final Job<?, ?> owner, final StaticAnalysisLabelProvider labelProvider, final int numberOfTools) {
        this(owner, labelProvider, numberOfTools, TrendChartType.TOOLS_ONLY);
    }

    /**
     * Creates a new instance of {@link JobAction}.
     *
     * @param owner
     *         the job that owns this action
     * @param labelProvider
     *         the label provider
     * @param numberOfTools
     *         the number of tools that have results to show
     * @param trendChartType
     *         determines if the trend chart will be shown
     */
    public JobAction(final Job<?, ?> owner, final StaticAnalysisLabelProvider labelProvider, final int numberOfTools,
            final TrendChartType trendChartType) {
        this.owner = owner;
        this.labelProvider = labelProvider;
        this.numberOfTools = numberOfTools;
        this.trendChartType = trendChartType;
    }

    /**
     * Returns the ID of this action and the ID of the associated results.
     *
     * @return the ID
     */
    public String getId() {
        return labelProvider.getId();
    }

    @Override
    public String getDisplayName() {
        return labelProvider.getLinkName();
    }

    /**
     * Returns the title of the trend graph.
     *
     * @return the title of the trend graph.
     */
    public String getTrendName() {
        return labelProvider.getTrendName();
    }

    /**
     * Returns the job this action belongs to.
     *
     * @return the job
     */
    public Job<?, ?> getOwner() {
        return owner;
    }

    /**
     * Returns the build history for this job.
     *
     * @return the history
     */
    public History createBuildHistory() {
        Run<?, ?> lastCompletedBuild = owner.getLastCompletedBuild();
        if (lastCompletedBuild == null) {
            return new NullAnalysisHistory();
        }
        else {
            return new AnalysisHistory(lastCompletedBuild, new ByIdResultSelector(labelProvider.getId()));
        }
    }

    /**
     * Returns the icon URL for the side-panel in the job screen. If there is no valid result yet, then {@code null} is
     * returned.
     *
     * @return the icon URL for the side-panel in the job screen
     */
    @Override
    @CheckForNull
    public String getIconFileName() {
        return createBuildHistory().getBaselineResult()
                .map(result -> Jenkins.RESOURCE_PATH + labelProvider.getSmallIconUrl())
                .orElse(null);
    }

    @Override
    public String getUrlName() {
        return labelProvider.getId();
    }

    /**
     * Redirects the index page to the last result.
     *
     * @param request
     *         Stapler request
     * @param response
     *         Stapler response
     *
     * @throws IOException
     *         in case of an error
     */
    @SuppressWarnings("unused") // Called by jelly view
    public void doIndex(final StaplerRequest request, final StaplerResponse response) throws IOException {
        Optional<ResultAction> action = getLatestAction();
        if (action.isPresent()) {
            response.sendRedirect2(String.format("../%d/%s", action.get().getOwner().getNumber(),
                    labelProvider.getId()));
        }
    }

    /**
     * Returns the latest static analysis results for this job.
     *
     * @return the latest results (if available)
     */
    public Optional<ResultAction> getLatestAction() {
        return createBuildHistory().getBaselineAction();
    }

    @JavaScriptMethod
    @Override
    public String getBuildTrendModel() {
        return new JacksonFacade().toJson(createChartModel());
    }

    private LinesChartModel createChartModel() {
        if (numberOfTools > 1) {
            return new ToolsTrendChart().create(createBuildHistory(), new ChartModelConfiguration());
        }
        else {
            return new SeverityTrendChart().create(createBuildHistory(), new ChartModelConfiguration());
        }
    }

    /**
     * Returns whether the trend chart is visible or not.
     *
     * @return {@code true} if the trend is visible, false otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    @Override
    public boolean isTrendVisible() {
        return trendChartType != TrendChartType.NONE && createBuildHistory().hasMultipleResults();
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", getClass().getName(), labelProvider.getName());
    }
}
