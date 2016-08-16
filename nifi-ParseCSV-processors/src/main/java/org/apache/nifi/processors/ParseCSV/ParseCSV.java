/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.ParseCSV;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.util.DefaultXmlPrettyPrinter;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import java.io.*;
import java.nio.charset.Charset;
import java.security.Key;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

@Tags({"csv", "parse", "masking", "mask", "tokenize", "encrypt"})
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class ParseCSV extends AbstractProcessor {

    public static final AllowableValue DEFAULT = new AllowableValue(
            "DEFAULT", "DEFAULT", "Standard comma separated format.");
    public static final AllowableValue EXCEL = new AllowableValue(
            "EXCEL", "EXCEL", "Excel file format (using a comma as the value delimiter). Note that the actual " +
            "value delimiter used by Excel is locale dependent, it might be necessary to customize " +
            "this format to accommodate to your regional settings.");
    public static final AllowableValue RFC4180 = new AllowableValue(
            "RFC4180", "RFC4180", "Common Format and MIME Type for Comma-Separated Values (CSV) Files: " +
            "<a href=\"http://tools.ietf.org/html/rfc4180\">RFC 4180</a>");
    public static final AllowableValue TDF = new AllowableValue(
            "TDF", "TDF", "Tab delimited format.");
    public static final AllowableValue MYSQL = new AllowableValue("MYSQL", "MYSQL", "Default MySQL format used " +
            "by the {@code SELECT INTO OUTFILE} and {@code LOAD DATA INFILE} operations.");

    public static final PropertyDescriptor FORMAT = new PropertyDescriptor
            .Builder().name("CSV Format")
            .description("Example Property")
            .required(true)
            .defaultValue(DEFAULT.getValue())
            .allowableValues(DEFAULT, EXCEL, RFC4180, TDF, MYSQL)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CREATE_ATTRIBUTES = new PropertyDescriptor
            .Builder().name("Create Attributes from records")
            .description("Example Property")
            .required(true)
            .defaultValue("False")
            .allowableValues("True", "False")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor DELIMITER = new PropertyDescriptor
            .Builder().name("File Delimiter")
            .description("Example Property")
            .required(true)
            .defaultValue(",")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor WITH_HEADER = new PropertyDescriptor
            .Builder().name("With Header")
            .description("Example Property")
            .required(true)
            .defaultValue("True")
            .allowableValues("True", "False")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final PropertyDescriptor STATIC_SCHEMA = new PropertyDescriptor
            .Builder().name("Static Schema")
            .description("Example Property")
            .required(false)
            .defaultValue("True")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    public static final PropertyDescriptor OUTPUT_FORMAT = new PropertyDescriptor
            .Builder().name("Standard Output Format")
            .description("")
            .required(false)
            .defaultValue("CSV")
            .allowableValues("CSV", "JSON", "XML")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CUSTOM_HEADER = new PropertyDescriptor
            .Builder().name("Custom Header")
            .description("Example Property")
            .required(false)
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor COLUMN_MASK = new PropertyDescriptor
            .Builder().name("Column Mask")
            .description("Example Property")
            .required(false)
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor COLUMN_ENCRYPT = new PropertyDescriptor
            .Builder().name("Column Encrypt")
            .description("Example Property")
            .required(false)
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor COLUMN_TOKENIZE = new PropertyDescriptor
            .Builder().name("Column Tokenize")
            .description("Example Property")
            .required(false)
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor TOKENIZE_UNQIUE_IDENTIFIER = new PropertyDescriptor
            .Builder().name("Tokenized Unique Identifier")
            .description("Use existing CSV column or RowNumber to extract record number as " +
                    "identifier as part of tokenization for mask and source data")
            .required(false)
            .defaultValue(null)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor TOKENIZED_OUTPUT = new PropertyDescriptor
            .Builder().name("Tokenized Output Format")
            .description("Store of where the tokenized source, mask and unique identifier values will be persisted.")
            .required(false)
            .defaultValue("APACHE PHOENIX")
            .allowableValues("APACHE PHOENIX", "MYSQL", "ORACLE", "MS SQL SERVER", "JSON", "XML", "CSV")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship RELATIONSHIP_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("success")
            .build();
    public static final Relationship RELATIONSHIP_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("failure")
            .build();
    public static final Relationship RELATIONSHIP_TOKENIZED = new Relationship.Builder()
            .name("tokenized")
            .description("tokenized")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(FORMAT);
        descriptors.add(CREATE_ATTRIBUTES);
        descriptors.add(DELIMITER);
        descriptors.add(WITH_HEADER);
        descriptors.add(STATIC_SCHEMA);
        descriptors.add(OUTPUT_FORMAT);
        descriptors.add(CUSTOM_HEADER);
        descriptors.add(COLUMN_MASK);
        descriptors.add(COLUMN_ENCRYPT);
        descriptors.add(COLUMN_TOKENIZE);
        descriptors.add(TOKENIZE_UNQIUE_IDENTIFIER);
        descriptors.add(TOKENIZED_OUTPUT);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(RELATIONSHIP_SUCCESS);
        relationships.add(RELATIONSHIP_FAILURE);
        relationships.add(RELATIONSHIP_TOKENIZED);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

        final Charset charset = Charset.defaultCharset();
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        // TODO implement
        final Map<String, String> attributes = new LinkedHashMap<>();
        final String format = context.getProperty(FORMAT).getValue();
        final boolean create_attributes = Boolean.parseBoolean(context.getProperty(CREATE_ATTRIBUTES).getValue());
        final char delimiter = context.getProperty(DELIMITER).getValue().charAt(0);
        final boolean with_header = Boolean.parseBoolean(context.getProperty(WITH_HEADER).getValue());
        final String output_format = context.getProperty(OUTPUT_FORMAT).getValue();
        final String custom_header = context.getProperty(CUSTOM_HEADER).getValue();
        final String column_mask = context.getProperty(COLUMN_MASK).getValue();
        final String column_encrypt = context.getProperty(COLUMN_ENCRYPT).getValue();
        final String column_tokenize = context.getProperty(COLUMN_TOKENIZE).getValue();
        final String tokenize_unique_identifier = context.getProperty(TOKENIZE_UNQIUE_IDENTIFIER).getValue();
        final String tokenized_ouput = context.getProperty(TOKENIZED_OUTPUT).getValue();
        final String encryptionKey = "Bar12345Bar12345";
        final String static_schema = context.getProperty(STATIC_SCHEMA).getValue();

        // new flowfile here
        final org.apache.nifi.util.ObjectHolder<FlowFile> holder = new org.apache.nifi.util.ObjectHolder<>(null);

        flowFile = session.write(flowFile, new StreamCallback() {
            @Override
            public void process(InputStream inputStream, OutputStream outputStream) throws IOException {

                CSVFormat csvFormat = buildFormat(format, delimiter, with_header, custom_header);
                CSVParser csvParser = new CSVParser(new InputStreamReader(inputStream, charset), csvFormat);
                CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outputStream, charset), csvFormat);
                String headerArray[];

                ArrayList<String> columnMaskList = new ArrayList<>();
                ArrayList<String> columnEncryptList = new ArrayList<String>();
                ArrayList<String> columnTokenizeList = new ArrayList<String>();

                List<String> maskValueHolder = new LinkedList<>();
                FlowFile tokenized = session.create();

                // print header if needed
                if (custom_header != null && output_format.equals("CSV") && static_schema == null) {
                    csvPrinter.printRecord(custom_header);
                    headerArray = custom_header.split(",");
                }
                else if (static_schema != null && custom_header == null)
                {
                    csvPrinter.printRecord(static_schema.replace("\"",""));
                    headerArray = static_schema.split(",");
                }
                else {
                    headerArray = csvParser.getHeaderMap().keySet().toArray(new String[0]);
                    csvPrinter.printRecord(headerArray);
                }

                if (column_mask != null) {
                    columnMaskList =
                            new ArrayList<>(Arrays.asList(column_mask.replace("\"","").split(",")));
                }

                if (column_encrypt != null) {
                    columnEncryptList =
                            new ArrayList<>(Arrays.asList(column_encrypt.split(",")));
                }

                if (column_tokenize != null) {
                    columnTokenizeList =
                            new ArrayList<>(Arrays.asList(column_tokenize.split(",")));
                }

                // loop through records and print
                for (final CSVRecord record : csvParser) {

                    Map<String, String> k = record.toMap();

                    for (Map.Entry<String,String> konj: k.entrySet())
                    {
                        //System.out.println(konj.getValue());
                    }
                    // generate attributes if required per record
                    if (create_attributes) {
                        for (int i = 0; i < headerArray.length; i++) {
                            //attributes.put(headerArray[i], record.get(i));
                            attributes.put(headerArray[i] + "." + record.getRecordNumber(), record.get(i));
                        }
                    }
                    // check masked columns
                    if (column_mask != null || column_encrypt != null) {
                        // we have to loop through the header array and match user requested mask columns
                        for (int i = 0; i < headerArray.length; i++) {
                            //System.out.println(headerArray[i] + "." + record.getRecordNumber() + " - " + mask(record.get(i)));

                            if (columnMaskList.contains(headerArray[i])) {
                                // set mask
                                maskValueHolder.add(mask(record.get(i)));

                                // construct tokenization row for external DB store
                                if (columnTokenizeList.contains(headerArray[i])) {
                                    final String tokenizedRow;
                                    tokenizedRow = tokenizationOut(tokenized_ouput, headerArray[i],
                                            tokenize_unique_identifier,mask(record.get(i)),record.get(i),
                                            Long.toString(record.getRecordNumber()));

                                    tokenized = session.append(tokenized, new OutputStreamCallback() {
                                        @Override
                                        public void process(OutputStream outputStream) throws IOException {
                                            outputStream.write(tokenizedRow.getBytes());
                                        }
                                    });
                                }
                            }
                            else if (columnEncryptList.contains(headerArray[i])) {
                                // encrypt
                                maskValueHolder.add(new String(Encrypt( record.get(i), encryptionKey), "UTF-8"));
                            }
                            else {
                                // no mask
                                maskValueHolder.add(record.get(i));
                            }
                        }
                        csvPrinter.printRecord(maskValueHolder);
                        // clear mask column holder
                        maskValueHolder.clear();
                    }
                    else {
                        // no masking or encryption required, print record
                        switch (output_format) {
                            case "CSV":
                                //csvPrinter.printRecord(record);
                                List<String> items = Arrays.asList(static_schema.split(","));
                                String lastColumn = items.get(items.size() - 1);
                                String test = "";
                                for (String item: items)
                                {
                                    if (item != lastColumn) {
                                        test += record.get(item) + ",";
                                    }
                                    else {
                                        test += record.get(item);
                                    }
                                }

                                csvPrinter.printRecord(test.replace("^\"|\"$", ""));
                                break;
                            case "JSON":
                                String json = new ObjectMapper().writer().withDefaultPrettyPrinter().
                                        writeValueAsString(record.toMap()) + "\n";
                                if (json.length() > 0) {
                                    outputStream.write(json.getBytes());
                                }

                                //List<Map<?, ?>> data = readObjectsFromCsv(inputStream);
                                //String adis = writeAsJson(data);
                                //outputStream.write(writeAsJson(data).getBytes());
                                break;
                            case "XML":
                                outputStream.write(new XmlMapper().writeValueAsString(record.toMap()).getBytes());
                                break;
                        }
                    }
                }
                csvPrinter.flush();
                csvPrinter.close();
                holder.set(tokenized);
            }
        });

        flowFile = session.putAllAttributes(flowFile, attributes);
        session.transfer(flowFile, RELATIONSHIP_SUCCESS);
        session.transfer(holder.get(), RELATIONSHIP_TOKENIZED);
    }

    private byte[] Encrypt(String data, String key) {
        byte[] returnEncrypted = null;
        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // encrypt the text
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            returnEncrypted = cipher.doFinal(Base64.encodeBase64(data.getBytes()));

            // decrypt the text
            //cipher.init(Cipher.DECRYPT_MODE, aesKey);
            //String decrypted = new String(cipher.doFinal(encrypted));
            //System.err.println(decrypted);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return Base64.encodeBase64(returnEncrypted);
    }

    public static List<Map<?, ?>> readObjectsFromCsv(InputStream is) throws IOException {
        CsvSchema bootstrap = CsvSchema.emptySchema().withHeader();
        CsvMapper csvMapper = new CsvMapper();
        MappingIterator<Map<?, ?>> mappingIterator = csvMapper.reader(Map.class).with(bootstrap).readValues(is);

        return mappingIterator.readAll();
    }

    public static String writeAsJson(List<Map<?, ?>> data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        //mapper.writeValue(file, data);
        return mapper.writer().withDefaultPrettyPrinter().writeValueAsString(data);
    }

    private String tokenizationOut (String store, String columnName, String uniqueIdentifier, String maskedValue, String sourceValue, String rowNumber) {
        String storeOutput = null;

        switch (store) {
            case "APACHE PHOENIX":
                storeOutput = "values ('" + columnName + "'" + "," +
                        "'" + (uniqueIdentifier.equals("RowNumber()") ?
                        rowNumber : uniqueIdentifier) + "'" + "," +
                        "'" + maskedValue + "'" + "," +
                        "'" + sourceValue + "')" + "\r\n";
                break;
            case "MySQL":
                storeOutput = "values ('" + columnName + "'" + "," +
                        "'" + (uniqueIdentifier.equals("RowNumber()") ?
                        rowNumber : uniqueIdentifier) + "'" + "," +
                        "'" + maskedValue + "'" + "," +
                        "'" + sourceValue + "')" + "\r\n";
                break;
            case "JSON":
                storeOutput = "{\"ColumnName\": " + "\"" + columnName + "\"," +
                        "\"UniqueIdentifier\": " + "\"" + (uniqueIdentifier.equals("RowNumber()") ?
                        rowNumber : uniqueIdentifier) + "\"," +
                        "\"MaskedValue\": " + "\"" + maskedValue + "\"," +
                        "\"SourceValue\": " + "\"" + sourceValue + "\"}" + "\r\n";
                break;
        }

        return storeOutput;
    }

    private CSVFormat buildFormat(String format, char delimiter, Boolean with_header, String custom_header) {
        CSVFormat csvFormat = null;

        // set pre built format
        if (format.equals("DEFAULT")) {
            csvFormat = CSVFormat.DEFAULT;
        } else if (format.equals("EXCEL")) {
            csvFormat = CSVFormat.EXCEL;
        }


        if (with_header & custom_header != null) {
            csvFormat = csvFormat.withSkipHeaderRecord(true);
            csvFormat = csvFormat.withHeader(custom_header);
        } else if (with_header & custom_header == null) {
            csvFormat = csvFormat.withHeader();
        }

        if (delimiter > 0) {
            csvFormat = csvFormat.withDelimiter(delimiter);
        }
        return csvFormat;
    }

    private String mask(String str) {

        final String consotant = "bcdfghjklmnpqrstvwxz";
        final String vowel = "aeiouy";
        final String digit = "0123456789";

        DateFormat dateFormat = new SimpleDateFormat("ssSSS");
        Date date = new Date();
        Random ran = new Random();
        Random r = new Random(Integer.parseInt(dateFormat.format(date) + ran.nextInt(1000)));

        char data[] = str.toCharArray();

        for (int n = 0; n < data.length; ++n) {
            char ln = Character.toLowerCase(data[n]);
            if (consotant.indexOf(ln) >= 0)
                data[n] = randomChar(r, consotant, ln != data[n]);
            else if (vowel.indexOf(ln) >= 0)
                data[n] = randomChar(r, vowel, ln != data[n]);
            else if (digit.indexOf(ln) >= 0)
                data[n] = randomChar(r, digit, ln != data[n]);
        }
        return new String(data);
    }

    private char randomChar(Random r, String cs, boolean uppercase) {
        char c = cs.charAt(r.nextInt(cs.length()));
        return uppercase ? Character.toUpperCase(c) : c;
    }
}
