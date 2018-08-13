package org.knime.core.data.convert.map;

import java.util.HashMap;
import java.util.stream.Stream;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.convert.datacell.JavaToDataCellConverter;
import org.knime.core.data.convert.datacell.JavaToDataCellConverterFactory;
import org.knime.core.data.convert.datacell.JavaToDataCellConverterRegistry;
import org.knime.core.data.convert.java.DataCellToJavaConverter;
import org.knime.core.data.convert.java.DataCellToJavaConverterFactory;
import org.knime.core.data.convert.java.DataCellToJavaConverterRegistry;
import org.knime.core.data.convert.map.Destination.ConsumerParameters;
import org.knime.core.data.convert.map.Source.ProducerParameters;
import org.knime.core.data.convert.util.SerializeUtil;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.ExecutionContext;

/**
 * Framework to map KNIME to external types vice versa.
 *
 * <p>
 * A frequent use case for KNIME nodes is to write or read data from an external storage. (A storage can be used as a
 * {@link Source source} or {@link Destination destination}.) In this case the value held by a KNIME {@link DataCell}
 * needs to be mapped to an external value.
 * </p>
 *
 * <p>
 * Extracting the Java value from a data cell is solved with {@link DataCellToJavaConverterRegistry} and
 * {@link JavaToDataCellConverterRegistry}. As custom KNIME extensions may implement custom {@link DataType data types},
 * it is impossible for nodes to support all possible types. Asking the custom type provider to implement mapping to all
 * different external sources and destinations (Python, SQL, H2O, Java, R and many more) is an impossible burden and
 * therefore we introduce Java objects as an intermediary representation for the mappings. An external type provider can
 * implement a {@link DataCellToJavaConverterFactory} or {@link JavaToDataCellConverterFactory} to extract a Java object
 * out of his custom cell and implementors or nodes which write to SQL, H2O, R, Python, etc. can then provide mapping to
 * a more limited set of Java types. Some cells may not be able to create a known java type (e.g. only some
 * VeryChemicalType) -- in which case we cannot support the type -- but usually such a type can be serialized to a blob
 * or String.
 * </p>
 *
 * <p>
 * Some external types do not have a Java representation. We can therefore not simply map from Java object to a Java
 * representation of an external type. Instead we wrap the concepts of {@link Source source} and {@link Destination
 * destination} and the concepts of "how to write to" ({@link CellValueConsumer}) and "how to read from"
 * ({@link CellValueProducer}) them. The destination and source are the external equivalent to a KNIME input or output
 * table. How to write/read from them is defined per set of types, but configurable via
 * {@link ConsumerParameters}/{@link ProducerParameters}. These parameters may include column index and row index, but
 * are fully dependent on the type of external storage and meant as a way in which the node communicates with an
 * instance of {@link CellValueConsumer} or {@link CellValueProducer}.
 * </p>
 *
 * <p>
 * With databases as storage, we have another special case where some databases are specializations of other databases
 * (e.g. Oracle databases a specialization of SQL databases). The specialization may support additional Java types or
 * additional external types.
 * </p>
 *
 * <p>
 * Finally, all mapping must be allowed to be done explicitly. Since automatic mappings are often not useful, a user
 * needs control of how to map certain KNIME types to the external types (the intermediate java representation is not
 * relevant) -- via a node dialog panel for example.
 * </p>
 *
 * <p>
 * For the dialog panel to be as generic and reusable as possible, its only input should be the {@link DataTableSpec}
 * and the type of destination or source. It then presents the user a per column list of available external types to map
 * to. This list can be queried from the framework using {@link ConsumerRegistry#getAvailableConsumptionPaths(DataType)}
 * or {@link ProducerRegistry#getAvailableProductionPaths(Object)}. Both return a list of glorified pairs which also
 * contain the information on how the intermediate java representation is extracted from or wrapped into a
 * {@link DataCell}. These can then be serialized from the dialog and read in a node model.
 * </p>
 *
 * <h1>Usage</h1>
 *
 * For any given destination type registering consumers is a matter of:
 *
 * <code lang="java"><pre>
 * // One time setup, e.g. in plugin initialisation:
 * MappingFramework.forDT(OracleSQLDatabaseDest.class) //
 *      .setParent(SQLDatabaseDest.class) // inherit less specific consumers
 *      .register(new SimpleDataValueConsumerFactory(
 *          String.class, "TEXT", (dest, value, params) -> { /* ... *{@literal /} }));
 * </pre></code>
 *
 * This will make certain consumption paths (pairs of {@link DataCellToJavaConverterFactory} and
 * {@link CellValueConsumerFactory}) available, which can be queried with
 * {@link ConsumerRegistry#getAvailableConsumptionPaths(DataType)}.
 *
 * For any given source type registering consumers is a matter of:
 *
 * <code><pre>
 * // One time setup, e.g. in plugin initialisation:
 * MappingFramework.forST(MyST.class) //
 *      .setParent(MyParentST.class) // inherit less specific producers
 *      .register(new SimpleDataValueProducerFactory(
 *          "TEXT", String.class, (dest, params) -> { /* ... *{@literal /} }));
 * </pre></code>
 *
 * This will make certain production paths (pairs of {@link JavaToDataCellConverterFactory} and
 * {@link CellValueProducerFactory}) available, which can be queried with
 * {@link ProducerRegistry#getAvailableProductionPaths(Object)}.
 *
 * @author Jonathan Hale, KNIME, Konstanz, Germany
 * @see SerializeUtil SerializeUtil - for serializing ConsumptionPath or ProductionPath.
 * @since 3.6
 */
public class MappingFramework {

    /* Do not instantiate */
    private MappingFramework() {
    }

    /**
     * Get the {@link CellValueConsumer} registry for given destination type.
     *
     * @param destinationType {@link Destination} type for which to get the registry
     * @return Per destination type consumer registry for given destination type.
     * @param <ET> External type
     * @param <D> Destination type
     */
    public static <ET, D extends Destination<ET>> ConsumerRegistry<ET, D>
        forDestinationType(final Class<? extends D> destinationType) {

        final ConsumerRegistry<ET, D> perDT = getConsumerRegistry(destinationType);
        if (perDT == null) {
            return createConsumerRegistry(destinationType);
        }

        return perDT;
    }

    /**
     * Get the {@link CellValueProducer} registry for given source type.
     *
     * @param sourceType {@link Source} type for which to get the registry
     * @return Per source type producer registry for given source type.
     * @param <ET> External type
     * @param <S> Source type
     */
    public static <ET, S extends Source<ET>> ProducerRegistry<ET, S>
        forSourceType(final Class<? extends S> sourceType) {
        final ProducerRegistry<ET, S> perST = getProducerRegistry(sourceType);
        if (perST == null) {
            return createProducerRegistry(sourceType);
        }

        return perST;
    }

    private static HashMap<Class<? extends Destination<?>>, ConsumerRegistry<?, ?>> m_destinationTypes =
        new HashMap<>();

    private static HashMap<Class<? extends Source<?>>, ProducerRegistry<?, ?>> m_sourceTypes = new HashMap<>();

    /* Get the consumer registry for given destination type */
    private static <ET, DT extends Destination<ET>> ConsumerRegistry<ET, DT>
        getConsumerRegistry(final Class<? extends DT> destinationType) {
        @SuppressWarnings("unchecked")
        final ConsumerRegistry<ET, DT> registry = (ConsumerRegistry<ET, DT>)m_destinationTypes.get(destinationType);
        return registry;
    }

    /* Get the producer registry for given destination type */
    private static <ET, S extends Source<ET>> ProducerRegistry<ET, S>
        getProducerRegistry(final Class<? extends S> sourceType) {
        @SuppressWarnings("unchecked")
        final ProducerRegistry<ET, S> registry = (ProducerRegistry<ET, S>)m_sourceTypes.get(sourceType);
        return registry;
    }

    /* Create the consumer registry for given destination type */
    private static <ET, D extends Destination<ET>> ConsumerRegistry<ET, D>
        createConsumerRegistry(final Class<? extends D> destinationType) {
        final ConsumerRegistry<ET, D> registry = new ConsumerRegistry<ET, D>();
        m_destinationTypes.put(destinationType, registry);
        return registry;
    }

    /* Create the producer registry for given destination type */
    private static <ET, S extends Source<ET>> ProducerRegistry<ET, S>
        createProducerRegistry(final Class<? extends S> sourceType) {
        final ProducerRegistry<ET, S> registry = new ProducerRegistry<ET, S>();
        m_sourceTypes.put(sourceType, registry);
        return registry;
    }

    /**
     * Map a row of input data from the given source to a {@link DataRow}.
     *
     * @param key Row key for the created row
     * @param source Source to get data from
     * @param mapping Production paths to take when reading a column and producting a {@link DataCell} from it.
     * @param params Per column parameters for the producers used
     * @param context Execution context potentially required to create converters
     * @return The DataRow which contains the data read from the source
     * @throws Exception If conversion fails
     * @param <ST> Source type
     * @param <PP> Producer parameters subclass
     */
    public static <S extends Source<?>, PP extends ProducerParameters<S>> DataRow map(final RowKey key,
        final S source, final ProductionPath[] mapping, final PP[] params, final ExecutionContext context)
        throws Exception {

        final DataCell[] cells = new DataCell[mapping.length];

        int i = 0;
        for (final ProductionPath path : mapping) {
            final JavaToDataCellConverter<?> converter = path.m_converterFactory.create(context);
            @SuppressWarnings("unchecked")
            final CellValueProducer<S, ?, PP> producer = (CellValueProducer<S, ?, PP>)path.m_producerFactory.create();

            cells[i] = converter.convertUnsafe(producer.produceCellValue(source, params[i]));
            ++i;
        }

        return new DefaultRow(key, cells);
    }

    /**
     * Map data from a {@link DataRow} to an external storage.
     *
     * @param row Row to map
     * @param dest Destination to write to
     * @param mapping Consumption paths to take when extracting a value from a cell and "consuming" to write to an
     *            external type.
     * @param params Per column parameters for the consumers used
     * @throws Exception If an exception occurs during conversion or mapping
     * @param <DT> Destination type
     * @param <CP> Consumer parameters subclass
     */
    public static <D extends Destination<?>, CP extends ConsumerParameters<D>> void map(final DataRow row,
        final D dest, final ConsumptionPath[] mapping, final CP[] params) throws Exception {

        int i = 0;
        for (final DataCell cell : row) {
            final DataCellToJavaConverter<?, ?> converter = mapping[i].m_converterFactory.create();
            @SuppressWarnings("unchecked")
            final CellValueConsumer<D, Object, CP> consumer =
                (CellValueConsumer<D, Object, CP>)mapping[i].m_consumerFactory.create();

            final Object cellValue = cell.isMissing() ? null : converter.convertUnsafe(cell);
            consumer.consumeCellValue(dest, cellValue, params[i]);
            ++i;
        }
    }

    /**
     * Create data table spec from column names and production paths.
     *
     * @param names Column names
     * @param paths Production paths
     * @return Spec of a table that would be mapped to using the given parameters.
     */
    public static DataTableSpec createSpec(final String[] names, final ProductionPath[] paths) {
        return new DataTableSpec(names,
            Stream.of(paths).map(path -> path.m_converterFactory.getDestinationType()).toArray(n -> new DataType[n]));
    }
}
