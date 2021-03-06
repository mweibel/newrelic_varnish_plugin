package de.bauerxcel.newrelic.plugins.varnish.instance;

import com.newrelic.metrics.publish.Agent;
import com.newrelic.metrics.publish.configuration.ConfigurationException;
import com.newrelic.metrics.publish.util.Logger;
import de.bauerxcel.newrelic.plugins.varnish.Metric;
import de.bauerxcel.newrelic.plugins.varnish.MetricMeta;
import de.bauerxcel.newrelic.plugins.varnish.VarnishStats;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * An agent for VarnishStats.
 *
 * @author Jan Schumann <jan.schumann@bauerxcel.de>
 */
public class VarnishAgent extends Agent {

    private static final String GUID = "de.bauerxcel.newrelic.plugins.varnish";
    private static final String VERSION = "1.1.0";
    private static final Logger LOGGER = Logger.getLogger(VarnishAgent.class);

    private String name;
    private VarnishStats stats;
    private Map<String, MetricMeta> meta;
    private Map<String, String> labels;
    private Boolean firstReport = true;
    private String agentInfo;

    /**
     * Constructor.
     */
    public VarnishAgent(String name, VarnishStats stats, Map<String, MetricMeta> meta, Map<String, String> labels) throws ConfigurationException {
        super(GUID, VERSION);

        this.name = name;
        this.stats = stats;
        this.meta = meta;
        this.labels = labels;
    }

    @Override
    public String getComponentHumanLabel() {
        return name;
    }

    @Override
    public void pollCycle() {
        LOGGER.debug("Gathering Varnish metrics. ", getAgentInfo());

        try {
            ArrayList<Metric> results = stats.fetch();   // Gather defined metrics
            reportMetrics(results);                      // Report Metrics to New Relic
        } catch (Exception e) {
            LOGGER.error("Faild to report: ", e.getMessage());
        }

        firstReport = false;
    }

    private void reportMetrics(ArrayList<Metric> results) {
        int count = 0;
        LOGGER.debug("Collected ", results.size(), " Varnish metrics. ", getAgentInfo());
        LOGGER.debug(results);

        Iterator<Metric> iter = results.iterator();
        while (iter.hasNext()) { // Iterate over current metrics
            Metric metric = iter.next();
            MetricMeta md = getMetricMeta(metric);
            if (!metric.isBitmap()) {
                if (md != null) { // Metric Meta data exists (from metric.category.json)
                    LOGGER.debug("Metric '", buildMetricSpec(metric), "' = '", metric.getValue(), "'");
                    count++;

                    if (metric.isCounter()) { // Metric is a counter
                        reportMetric(buildMetricSpec(metric), md.getUnit() + "/Second", md.getCounter().process(metric.getValue()));
                    } else { // Metric is a fixed Number
                        String unit = md.getUnit();
                        if (metric.isGauge()) {
                            unit += "/Second";
                        }
                        reportMetric(buildMetricSpec(metric), unit, metric.getValue());
                    }
                } else { // md != null
                    if (firstReport) { // Provide some feedback of available metrics for future reporting
                        LOGGER.debug("Not reporting identified metric ", buildMetricSpec(metric));
                    }
                }
            } else { // bitmap values are not supported
                if (firstReport) { // Provide some feedback of unsupported metrics for future reporting
                    LOGGER.debug("Not reporting unsupported metric ", buildMetricSpec(metric));
                }
            }

        }

        LOGGER.debug("Reported to New Relic ", count, " metrics. ", getAgentInfo());
    }

    private MetricMeta getMetricMeta(Metric metric) {
        MetricMeta meta = this.meta.get(metric.getType() + "/" + metric.getName());
        if (null != meta && metric.hasIdent()) {
            // if an identifier exists, multiple meta objects with the same name exist
            if (this.meta.containsKey(metric.getType() + "/" + metric.getIdent() + "/" + metric.getName())) {
                meta = this.meta.get(metric.getType() + "/" + metric.getIdent() + "/" + metric.getName());
            } else {
                // we have to clone the originally created meta object which did not contain the identifier in the key
                meta = new MetricMeta(meta);
                this.meta.put(metric.getType() + "/" + metric.getIdent() + "/" + metric.getName(), meta);
            }
        }

        return meta;
    }

    private String buildMetricSpec(Metric metric) {
        StringBuilder spec = new StringBuilder();
        spec.append("Varnish").append("/").append(metric.getType());
        if (metric.hasIdent()) {
            spec.append("/").append(metric.getIdent());
        }
        if (labels.containsKey(metric.getName())) {
            spec.append("/").append(labels.get(metric.getName()));
        }
        else {
            spec.append("/").append(metric.getLabel());
        }

        return spec.toString();
    }

    private String getAgentInfo() {
        if (agentInfo == null) {
            agentInfo = new StringBuilder()
                    .append("Agent Name: ")
                    .append(name)
                    .append(". Agent Version: ")
                    .append(VERSION)
                    .toString();
        }
        return agentInfo;
    }

}
