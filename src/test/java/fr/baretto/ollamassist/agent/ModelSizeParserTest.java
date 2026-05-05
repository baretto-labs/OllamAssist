package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.utils.ModelSizeParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelSizeParserTest {

    @Test
    void extractsIntegerBillions() {
        assertThat(ModelSizeParser.extractParamCountBillions("qwen3:8b")).isEqualTo(8.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("qwen2.5:14b")).isEqualTo(14.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("llama3.2:3b")).isEqualTo(3.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("mistral-nemo:12b")).isEqualTo(12.0, within(0.01));
    }

    @Test
    void extractsDecimalBillions() {
        assertThat(ModelSizeParser.extractParamCountBillions("deepseek-r1:1.5b")).isEqualTo(1.5, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("qwen2.5:0.5b")).isEqualTo(0.5, within(0.01));
    }

    @Test
    void extractsFromMoEModelName() {
        // qwen3:30b-a3b — total 30B, active 3B; we parse the first :Xb occurrence (30)
        assertThat(ModelSizeParser.extractParamCountBillions("qwen3:30b-a3b")).isEqualTo(30.0, within(0.01));
    }

    @Test
    void returnsMinusOneWhenSizeUnknown() {
        assertThat(ModelSizeParser.extractParamCountBillions("qwen3:latest")).isEqualTo(-1.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("qwen3")).isEqualTo(-1.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions("")).isEqualTo(-1.0, within(0.01));
        assertThat(ModelSizeParser.extractParamCountBillions(null)).isEqualTo(-1.0, within(0.01));
    }

    @Test
    void isCaseInsensitive() {
        assertThat(ModelSizeParser.extractParamCountBillions("Model:7B")).isEqualTo(7.0, within(0.01));
    }
}
