<template>
  <div class="accordion-item">
    <h2 class="accordion-header" :id="'heading' + id">
      <button
        class="accordion-button"
        type="button"
        data-bs-toggle="collapse"
        :aria-expanded="open"
        :aria-controls="'item' + id"
        :class="!open ? 'collapsed' : ''"
        :data-bs-target="'#item' + id"
      >
        <span>
          {{ title }}
        </span>
      </button>
    </h2>
    <div
      class="accordion-collapse collapse"
      :aria-labelledby="'heading' + id"
      :data-bs-parent="parentId()"
      :id="'item' + id"
      :class="open ? 'show' : ''"
    >
      <div class="accordion-body">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
#meetings-tool {
.accordion-item {
  border: thin solid var(--sakai-border-color);
}
.accordion-button {
  background-color: var(--sakai-background-color-1);
  color: var(--sakai-text-color);
}
.accordion-button:focus{
  outline: 3px solid var(--focus-outline-color);
}
.accordion-button:not(.collapsed) {
  background-color: var(--sakai-background-color-2);
  color: var(--sakai-link-color);
  box-shadow: inset 0 calc(-1 * var(--sakai-button-border-width)) 0 var(--sakai-border-color)
}
.accordion-body {
  background-color: var(--sakai-background-color-1);
}
.accordion-button:not(.collapsed)::after {
  background-image: none;
  content: "\f077";
  display: inline-block;
  font: normal normal normal 14px/1 FontAwesome;
  font-size: inherit;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
}
.accordion-button.collapsed::after {
  background-image: none;
  content: "\f077";
  display: inline-block;
  font: normal normal normal 14px/1 FontAwesome;
  font-size: inherit;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
}}
</style>

<script>
// eslint-disable-next-line
import { Collapse } from "bootstrap";
import { v4 as uuid } from "uuid";
export default {
  data() {
    return {
      id: "accordion",
    };
  },
  props: {
    title: {
      Type: String,
      default: "Please define heading with the title prop",
    },
    open: {
      Type: [String, Boolean],
      default: false,
      validator(value) {
        return [true, "true", false, "false"].indexOf(value) > -1;
      },
    },
  },
  computed: {
    showsOpen() {
      return this.open;
    },
  },
  methods: {
    parentId() {
      //Only set a parentId if we want the accordions to close automatically
      if (!this.independent()) {
        return "#" + this.$parent.$data.id;
      }
      return null;
    },
    independent() {
      return true;
    },
  },
  created() {
    this.id = uuid().substring(8, 13); //random id '-34F4'
  },
};
</script>
