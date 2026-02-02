import type {Prism} from 'prism-react-renderer';

export default function constellationLang(Prism: typeof import('prismjs')) {
  Prism.languages['constellation'] = {
    comment: {
      pattern: /#.*/,
      greedy: true,
    },
    string: {
      pattern: /"(?:[^"\\]|\\.)*"/,
      greedy: true,
    },
    keyword: [
      {
        pattern: /\b(?:in|out|type|when|otherwise|branch|with)\b/,
      },
    ],
    builtin: {
      pattern:
        /\b(?:String|Int|Float|Boolean|List|Optional|Candidates|Record|Any|Nothing)\b/,
    },
    boolean: /\b(?:true|false)\b/,
    number: /\b\d+(?:\.\d+)?\b/,
    'function': {
      pattern: /\b[A-Z][a-zA-Z0-9]*(?=\s*\()/,
    },
    'type-definition': {
      pattern: /(?<=type\s+)[A-Z][a-zA-Z0-9]*/,
      alias: 'class-name',
    },
    operator: /\+\+?|->|=>|>=|<=|!=|==|\?\?|[+\-*/><]/,
    punctuation: /[{}()\[\]:,=.]/,
    variable: /\b[a-z_][a-zA-Z0-9_]*\b/,
  };
}
