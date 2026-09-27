/**
 * Created by: artDev
 * Copyright (c) 2025 artDev, SerpentSpirale, CADIndie.
 * For use under LGPL-3.0
 */

#include "unordered_map/unordered_map.h"
#include "vgpu_shaderconv/shaderconv.h"
#include "glsl_optimizer/src/code/c_wrapper.h"
#include <GLES3/gl3.h>
#include <string.h>
#include "string_utils.h"
#include "egl.h"
#include "proc.h"

struct def_uval {
    struct def_uval* next;
    char name[128];
    char type;
    int vector_len;
    union {
        GLint iv[4];
        GLuint uv[4];
        GLfloat fv[4];
    };
};

typedef struct {
    struct def_uval* first;
    struct def_uval* last;
} uval_list_t;

typedef struct {
    GLenum shader_type;
    const GLchar* source;
    uval_list_t uval_list;
} shader_info_t;

typedef struct {
    GLuint frag_shader;
    GLchar* colorbindings[MAX_DRAWBUFFERS];
    uval_list_t uval_list;
} program_info_t;

static void uval_list_add(uval_list_t* list, struct def_uval entry) {
    const size_t struct_size = sizeof(struct def_uval);
    struct def_uval* new_entry = malloc(struct_size);
    memcpy(new_entry, &entry, struct_size);
    new_entry->next = NULL;
    if(list->first == NULL) {
        list->first = list->last = new_entry;
    }else {
        list->last->next = new_entry;
        list->last = new_entry;
    }
}

static void uval_list_add_unique(uval_list_t* list, struct def_uval entry) {
    for(struct def_uval *current = list->first; current != NULL; current = current->next) {
        if(strcmp(current->name, entry.name) == 0) return;
    }
    uval_list_add(list, entry);
}

static void uval_list_free(uval_list_t* list) {
    struct def_uval *current = list->first;
    for(;current != NULL;) {
        void* old_current = current;
        current = current->next;
        free(old_current);
    }
}

static void uval_apply(GLint loc, const struct def_uval* uval) {
#define APPLY_4(type, arr) es3_functions.glUniform4##type(loc, uval->arr[0], uval->arr[1], uval->arr[2], uval->arr[3])
#define APPLY_3(type, arr) es3_functions.glUniform3##type(loc, uval->arr[0], uval->arr[1], uval->arr[2])
#define APPLY_2(type, arr) es3_functions.glUniform2##type(loc, uval->arr[0], uval->arr[1])
#define APPLY_1(type, arr) es3_functions.glUniform1##type(loc, uval->arr[0])
#define APPLY_LEN(len) \
    case len:                   \
    switch (uval->type) { \
        case 'i': APPLY_##len(i, iv); break; \
        case 'f': APPLY_##len(f, fv); break; \
        case 'u': APPLY_##len(ui, uv); break; \
    }                  \
    break;

    switch (uval->vector_len) {
        APPLY_LEN(1)
        APPLY_LEN(2)
        APPLY_LEN(3)
        APPLY_LEN(4)
    }

#undef APPLY_LEN
#undef APPLY_1
#undef APPLY_2
#undef APPLY_3
#undef APPLY_4
}

static void uval_list_apply(uval_list_t* list, GLuint program) {
    GLint oldprogram;
    es3_functions.glGetIntegerv(GL_CURRENT_PROGRAM, &oldprogram);
    es3_functions.glUseProgram(program);
    for(struct def_uval *current = list->first; current != NULL; current = current->next) {
        GLint location = es3_functions.glGetUniformLocation(program, current->name);
        if(location == -1) continue;
        uval_apply(location, current);
    }
}


GLuint glCreateProgram(void) {
    if(!current_context) return 0;
    GLuint phys_program = es3_functions.glCreateProgram();
    if(phys_program == 0) return phys_program;
    program_info_t *prog_info = calloc(1, sizeof(program_info_t));
    if(prog_info == NULL) {
        printf("LTWShdrWp: failed to allocate program_info\n");
        abort();
    }
    unordered_map_put(current_context->program_map, (void*)phys_program, prog_info);
    return phys_program;
}

void glDeleteProgram(GLuint program) {
    if(!current_context) return;
    es3_functions.glDeleteProgram(program);
    program_info_t *old_programinfo = unordered_map_remove(current_context->program_map, (void*)program);
    if(old_programinfo == NULL) return;
    for(GLuint i = 0; i < MAX_DRAWBUFFERS; i++) {
        const GLchar* binding = old_programinfo->colorbindings[i];
        if(binding != NULL) free((void*)binding);
    }
    uval_list_free(&old_programinfo->uval_list);
    free(old_programinfo);
}

void glAttachShader( 	GLuint program,
                        GLuint shader) {
    if(!current_context) return;
    es3_functions.glAttachShader(program, shader);
    program_info_t* program_info = unordered_map_get(current_context->program_map, (void*)program);
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(program_info == NULL || shader_info == NULL) return;
    if(shader_info->shader_type == GL_FRAGMENT_SHADER) program_info->frag_shader = shader;

    struct def_uval *current = shader_info->uval_list.first;
    for(;current != NULL; current = current->next) {
        uval_list_add_unique(&program_info->uval_list, *current);
    }
}

void glBindFragDataLocation( 	GLuint program,
                                GLuint colorNumber,
                                const char * name) {
    if(!current_context) return;
    program_info_t *program_info = unordered_map_get(current_context->program_map, (void*)program);
    if(program_info == NULL || colorNumber >= MAX_DRAWBUFFERS) return;
    // Insert binding name at the specific index
    GLchar** pname = &program_info->colorbindings[colorNumber];
    if(asprintf(pname, "%s", name) == -1) {
        *pname = NULL;
    }
}

void glGetShaderiv(GLuint shader, GLuint pname, GLint* params) {
    if(!current_context) return;
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(shader_info != NULL && shader_info->shader_type == GL_FRAGMENT_SHADER && pname == GL_COMPILE_STATUS) {
        // HACK: ignore compile results for frag shaders, as some drivers may not compile them without explicit fragouts
        // (which we add at link-time)
        *params = GL_TRUE;
        return;
    }
    es3_functions.glGetShaderiv(shader, pname, params);
}

static void insert_fragout_pos(char* source, int* size, const char* name, GLuint pos) {
    char src_string[256] = { 0 };
    char dst_string[256] = { 0 };
    snprintf(src_string, sizeof(src_string), "/* LTW INSERT LOCATION %s LTW */", name);
    snprintf(dst_string, sizeof(dst_string), "layout(location = %u) ", pos);
    gl4es_inplace_replace_simple(source, size, src_string, dst_string);
}

static GLuint patch_fragdata_loc(program_info_t* program_info) {
    if(program_info->frag_shader == 0) return 0;
    shader_info_t *shader = unordered_map_get(current_context->shader_map, (void*)program_info->frag_shader);
    if(shader == NULL) {
        printf("LTWShdrWp: failed to patch frag data location due to missing shader info\n");
        return 0;
    }
    int nsrc_size = (int)(strlen(shader->source) + 1);
    char* new_source = malloc(nsrc_size);
    memcpy(new_source, shader->source, nsrc_size);
    bool changesMade = false;
    for(GLuint i = 0; i < MAX_DRAWBUFFERS; i++) {
        const char* colorbind = program_info->colorbindings[i];
        if(colorbind == NULL) continue;
        insert_fragout_pos(new_source, &nsrc_size, colorbind, i);
        changesMade = true;
    }
    if(!changesMade) {
        free(new_source);
        return 0;
    }else {
        //printf("\n\n\nShader Result POST PATCH\n%s\n\n\n", new_source);
    }
    const GLchar* const_source = (const GLchar*)new_source;
    GLuint patched_shader = es3_functions.glCreateShader(GL_FRAGMENT_SHADER);
    if(patched_shader == 0) {
        free(new_source);
        printf("LTWShdrWp: failed to initialize patched shader\n");
        return 0;
    }
    es3_functions.glShaderSource(patched_shader, 1, &const_source, NULL);
    es3_functions.glCompileShader(patched_shader);
    free(new_source);
    GLint compileStatus;
    es3_functions.glGetShaderiv(patched_shader, GL_COMPILE_STATUS, &compileStatus);
    if(compileStatus != GL_TRUE) {
        GLint logSize;
        es3_functions.glGetShaderiv(patched_shader, GL_INFO_LOG_LENGTH, &logSize);
        GLchar log[logSize];
        es3_functions.glGetShaderInfoLog(patched_shader, logSize, NULL, log);
        es3_functions.glDeleteShader(patched_shader);
        printf("LTWShdrWp: failed to compile patched fragment shader, using default. Log:\n\n%s\n\nShader content:\n\n%s\n\n", log, const_source);
        return 0;
    }
    return patched_shader;
}

void glLinkProgram(GLuint program) {
    if(!current_context) return;
    program_info_t* program_info = unordered_map_get(current_context->program_map, (void*)program);
    if(program_info == NULL) {
        printf("LTWShdrWp: program info missing for program %u\n", program);
        es3_functions.glLinkProgram(program);
        return;
    }
    GLuint patched_frag_shader = patch_fragdata_loc(program_info);
    if(patched_frag_shader != 0) {
        es3_functions.glDetachShader(program, program_info->frag_shader);
        es3_functions.glAttachShader(program, patched_frag_shader);
    }
    es3_functions.glLinkProgram(program);
    if(patched_frag_shader != 0) es3_functions.glDeleteShader(patched_frag_shader);
    uval_list_apply(&program_info->uval_list, program);
    return;
}

static const char* vec1_initializer = "  %%%c ";
static const char* vec2_initializer = " %svec2(%%%2$c, %%%2$c) ";
static const char* vec3_initializer = " %svec3(%%%2$c, %%%2$c, %%%2$c) ";
static const char* vec4_initializer = " %svec4(%%%2$c, %%%2$c, %%%2$c, %%%2$c) ";

static void print_scan_template(char type, int veclen, char out[64]) {
    char type_str[] = {type , 0};
    if(type == 'f') type_str[0] = 0;
    switch (veclen) {
        case 1: snprintf(out, sizeof(char) * 64, vec1_initializer, type); break;
        case 2: snprintf(out, sizeof(char) * 64, vec2_initializer, type_str, type); break;
        case 3: snprintf(out, sizeof(char) * 64, vec3_initializer, type_str, type); break;
        case 4: snprintf(out, sizeof(char) * 64, vec4_initializer, type_str, type); break;
        default:
            abort();
    }
}

static void find_constant_uniforms(shader_info_t * programInfo, const char* source) {
    char vector_scan_template[64] = { 0 }, var_value[128] = { 0 };
    float initval[4];
    for(;;) {
        source = strstr(source, "/* LTW UNIFORM INIT");
        if(source == NULL) break;
        struct def_uval entry;
        if(sscanf(source, "/* LTW UNIFORM INIT %c %i %127s %127s */", &entry.type, &entry.vector_len, entry.name, var_value) < 2) {
            abort();
        }
        print_scan_template(entry.type, entry.vector_len, vector_scan_template);
        int scancnt = 0;
        switch (entry.type) {
            case 'f': scancnt = sscanf(var_value, vector_scan_template, &entry.fv[0], &entry.fv[1], &entry.fv[2], &entry.fv[3]); break;
            case 'i': scancnt = sscanf(var_value, vector_scan_template, &entry.iv[0], &entry.iv[1], &entry.iv[2], &entry.iv[3]); break;
            case 'u': scancnt = sscanf(var_value, vector_scan_template, &entry.uv[0], &entry.uv[1], &entry.uv[2], &entry.uv[3]); break;
        }
        if(scancnt != entry.vector_len) {
            printf("LTWShdrWp: could not scan for initializer, value %s\n template %s\n", var_value, vector_scan_template);
            abort();
        }
        uval_list_add(&programInfo->uval_list, entry);
        source = gl4es_next_line(source);
    }
}

GLuint glCreateShader(GLenum shaderType) {
    if(!current_context) return 0;
    GLuint phys_shader = es3_functions.glCreateShader(shaderType);
    if(phys_shader == 0) return 0;
    shader_info_t* info_struct = calloc(1, sizeof(shader_info_t));
    if(info_struct == NULL) {
        printf("LTWShdrWp: failed to allocate shader_info\n");
        abort();
    }
    info_struct->shader_type = shaderType;
    unordered_map_put(current_context->shader_map, (void*)phys_shader, info_struct);
    return phys_shader;
}

void glDeleteShader(GLuint shader) {
    if(!current_context) return;
    es3_functions.glDeleteShader(shader);
    shader_info_t * old_shaderinfo = unordered_map_remove(current_context->shader_map, (void*)shader);
    if(old_shaderinfo == NULL) return;
    if(old_shaderinfo->source != NULL) free((void*)old_shaderinfo->source);
    uval_list_free(&old_shaderinfo->uval_list);
    free(old_shaderinfo);
}

void glShaderSource(GLuint shader, GLsizei count, const GLchar *const*string, const GLint *length) {
    if(!current_context) return;
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(shader_info == NULL) {
        printf("LTWShdrWp: shader_info missing for shader %u\n", shader);
        es3_functions.glShaderSource(shader, count, string, length);
        return;
    }

    size_t target_length = 0;
#define SRC_LEN(x) length != NULL ? length[x] : strlen(string[x])
    for(GLsizei i = 0; i < count; i++) target_length += SRC_LEN(i);
    GLchar* target_string = malloc((target_length + 1) * sizeof(GLchar));
    size_t offset = 0;
    for(GLsizei i = 0; i < count; i++) {
        memcpy(&target_string[offset], string[i], SRC_LEN(i));
    }
    target_string[target_length] = 0;

#undef SRC_LEN
    GLchar* new_source = optimize_shader(target_string, shader_info->shader_type, 460, current_context->shader_version);
    if(new_source != NULL) {
        find_constant_uniforms(shader_info, new_source);
    }
    //printf("\n\n\nShader Result\n%s\n\n\n", new_source);
    if(shader_info->source != NULL) free((void*)shader_info->source);
    shader_info->source = new_source;
    es3_functions.glShaderSource(shader, 1, &shader_info->source, 0);
    free(target_string);
}
