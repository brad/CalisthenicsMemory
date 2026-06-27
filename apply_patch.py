import sys

def apply_patch(file_path, patch_path):
    with open(file_path, 'r') as f:
        content = f.read()

    with open(patch_path, 'r') as f:
        patch = f.read()

    parts = patch.split('<<<<<<< SEARCH\n')
    for part in parts[1:]:
        search_replace = part.split('=======\n')
        search = search_replace[0]
        replace = search_replace[1].split('>>>>>>> REPLACE\n')[0]

        if search in content:
            content = content.replace(search, replace)
        else:
            print(f"Error: Search block not found in {file_path}")
            # print(f"Search block:\n{search}")
            sys.exit(1)

    with open(file_path, 'w') as f:
        f.write(content)
    print(f"Successfully patched {file_path}")

if __name__ == "__main__":
    apply_patch(sys.argv[1], sys.argv[2])
